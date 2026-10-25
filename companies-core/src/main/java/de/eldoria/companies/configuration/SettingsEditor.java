/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.data.repository.ANodeData;
import dev.chojo.ocular.key.Key;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Changes settings of a shared file as the dashboard's settings editor submits them: each value is checked against its
 * {@link SettingsCatalogue} type, applied to the revision the admin saw, read back as the file's settings, and stored as
 * the next revision when nobody stored one meanwhile.
 * <p>
 * Every bad value is named at once and nothing is stored then. A secret is written only when a value is given; none
 * keeps it, an empty one clears it, one of only spaces is refused, and one set by an environment variable cannot be changed here. When another
 * revision was stored after the one the admin saw, nothing is stored; the answer carries the newer values of the
 * submitted settings and which settings that revision changed, so the admin can save again on top of it.
 */
public final class SettingsEditor {
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private SettingsEditor() {}

    /**
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions
     * @param file          the shared file
     * @param base          the revision the admin saw
     * @param values        the changed settings by dotted key; a secret's value is null to keep it
     * @param actor         who saved
     * @return what happened
     */
    public static Result save(
            Configuration configuration,
            ANodeData nodeData,
            String file,
            int base,
            Map<String, JsonNode> values,
            String actor) {
        var key = configuration.sharedKey(file).orElseThrow(() -> new IllegalArgumentException(file));
        var errors = new LinkedHashMap<String, String>();
        values.forEach((name, value) ->
                problem(configuration, file, name, value).ifPresent(problem -> errors.put(name, problem)));
        if (!errors.isEmpty()) return new Result.Invalid(errors);
        var latest = nodeData.settingsRevisions().getOrDefault(file, 0);
        if (base < 0 || base > latest) return new Result.Invalid(Map.of("", "invalid"));
        if (latest != base) return newer(nodeData, file, base, latest, values.keySet());
        var tree = nodeData.settingsAt(file, base)
                .map(revision -> tree(revision.content()))
                .orElseGet(() -> configuration.currentTree(file).orElse(MissingNode.getInstance()));
        var edited = tree.isObject() ? ((ObjectNode) tree).deepCopy() : JsonNodeFactory.instance.objectNode();
        values.forEach((name, value) -> {
            if (value == null || value.isNull()) return;
            put(edited, SettingsCatalogue.field(file, name).orElseThrow(), value);
        });
        String content;
        try {
            content = normalized(key, configuration, nodeData, YAML.writeValueAsString(edited));
        } catch (RuntimeException e) {
            return new Result.Invalid(Map.of("", "invalid"));
        }
        var restart = values.keySet().stream()
                .filter(name ->
                        SettingsCatalogue.field(file, name).orElseThrow().restart())
                .sorted()
                .toList();
        var outcome = configuration.store(nodeData, file, base, content, actor).orElseThrow();
        if (outcome instanceof Configuration.StoreOutcome.Newer newer) {
            return newer(nodeData, file, base, newer.latest(), values.keySet());
        }
        return new Result.Done(outcome, restart);
    }

    private static <T> String normalized(Key<T> key, Configuration configuration, ANodeData nodeData, String yaml) {
        return nodeData.writeSettings(key, configuration, nodeData.readSettings(key, configuration, yaml));
    }

    private static Result newer(ANodeData nodeData, String file, int base, int latest, Iterable<String> names) {
        var before = nodeData.settingsAt(file, base)
                .map(revision -> tree(revision.content()))
                .orElse(MissingNode.getInstance());
        var after = nodeData.settingsAt(file, latest)
                .map(revision -> tree(revision.content()))
                .orElse(MissingNode.getInstance());
        var current = new LinkedHashMap<String, JsonNode>();
        for (var name : names) {
            var field = SettingsCatalogue.field(file, name).orElseThrow();
            var value = field.at(after);
            if (!field.secret() && !value.isMissingNode()) current.put(name, value);
        }
        return new Result.Newer(latest, current, SettingsCatalogue.changed(file, before, after));
    }

    private static Optional<String> problem(Configuration configuration, String file, String name, JsonNode value) {
        var field = SettingsCatalogue.field(file, name);
        if (field.isEmpty()) return Optional.of("unknown");
        var valid =
                switch (field.get().type()) {
                    case FLAG -> value.isBoolean();
                    case WHOLE -> value.isIntegralNumber() && value.canConvertToInt();
                    case DECIMAL -> value.isNumber();
                    case TEXT -> value.isString();
                    case CHOICE -> value.isString() && field.get().choices().contains(value.asString());
                    case CHOICES ->
                        value.isArray()
                                && value.valueStream()
                                        .allMatch(entry -> entry.isString()
                                                && field.get().choices().contains(entry.asString()));
                    case TEXT_LIST -> value.isArray() && value.valueStream().allMatch(JsonNode::isString);
                    case FLAG_MAP -> value.isObject() && value.valueStream().allMatch(JsonNode::isBoolean);
                    case SECRET -> value.isNull() || value.isString();
                };
        if (!valid) return Optional.of(field.get().type().name().toLowerCase(Locale.ROOT));
        if (value.isNumber()) {
            var number = value.asDouble();
            if (!Double.isFinite(number)) return Optional.of("decimal");
            var min = field.get().min();
            var max = field.get().max();
            if (min != null && number < min) return Optional.of("range");
            if (max != null && number > max) return Optional.of("range");
        }
        if (field.get().secret()
                && value.isString()
                && value.asString().isBlank()
                && !value.asString().isEmpty()) {
            return Optional.of("secret");
        }
        if (name.equals("generalSettings.timezone") && !zone(value.asString())) return Optional.of("timezone");
        if (field.get().secret()
                && value.isString()
                && configuration.discordSettings().urlFromEnvironment()) {
            return Optional.of("environment");
        }
        return Optional.empty();
    }

    private static boolean zone(String name) {
        if (name.isBlank()) return true;
        try {
            ZoneId.of(name.strip());
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static void put(ObjectNode tree, SettingsCatalogue.Field field, JsonNode value) {
        var path = field.path();
        var node = tree;
        for (var part : path.subList(0, path.size() - 1)) {
            var next = node.get(part);
            if (next == null || !next.isObject()) {
                next = node.putObject(part);
            }
            node = (ObjectNode) next;
        }
        node.set(
                path.getLast(),
                field.secret()
                        ? JsonNodeFactory.instance.stringNode(value.asString().strip())
                        : value);
    }

    private static JsonNode tree(String content) {
        try {
            return YAML.readTree(content);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }

    /**
     * What a save did.
     */
    public sealed interface Result {
        /**
         * Some values were refused; nothing was stored.
         *
         * @param fields the reason by dotted key: the type the value should have ({@code flag}, {@code whole},
         *               {@code decimal}, {@code text}, {@code choice}, {@code choices}, {@code text_list},
         *               {@code flag_map}, {@code secret}), {@code range} outside its bounds,
         *               {@code timezone} for a zone that does not exist, {@code unknown}, {@code environment}, or
         *               {@code invalid} under the empty key when the file as a whole could not be read or the
         *               revision edited does not exist
         */
        record Invalid(Map<String, String> fields) implements Result {}

        /**
         * Another revision was stored after the one the admin saw; nothing was stored.
         *
         * @param latest  the newer revision
         * @param values  the submitted settings as the newer revision holds them, without secrets
         * @param changed the settings the newer revisions changed since the one the admin saw
         */
        record Newer(int latest, Map<String, JsonNode> values, List<String> changed) implements Result {}

        /**
         * The values were stored, or equalled the latest revision.
         *
         * @param outcome what storing did
         * @param restart the saved settings that apply only after a restart
         */
        record Done(Configuration.StoreOutcome outcome, List<String> restart) implements Result {}
    }
}
