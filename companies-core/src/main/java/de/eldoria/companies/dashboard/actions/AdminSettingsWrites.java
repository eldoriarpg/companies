/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.SettingsCatalogue;
import de.eldoria.companies.dashboard.views.Payloads;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.nodes.SettingsChange;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The dashboard's view of the shared settings, as {@code /companyadmin settings} shows them in game: a file as its
 * latest revision with every setting of the {@link SettingsCatalogue}, how the servers stand on it, its history and
 * what one revision changed.
 * <p>
 * Values are read from the latest revision in the database, not from the answering node's memory, so every node
 * answers the same. A secret is never sent, only whether it is set and where from. Every action needs the general
 * admin permission. The methods block and are called off the main thread.
 */
public final class AdminSettingsWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS =
            Set.of(Actions.ADMIN_SETTINGS_GET, Actions.ADMIN_SETTINGS_HISTORY, Actions.ADMIN_SETTINGS_REVISION);

    /**
     * The revisions of one history page.
     */
    public static final int PAGE_SIZE = 20;

    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String INVALID = "dashboard.invalid";
    private static final String UNKNOWN_REVISION = "companyadmin.settings.error.unknownRevision";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the settings are read from
     */
    public AdminSettingsWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs a read of the shared settings.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.ADMIN)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        try {
            var file = file(args);
            return switch (request.name()) {
                case Actions.ADMIN_SETTINGS_GET -> answers.data(request, get(file));
                case Actions.ADMIN_SETTINGS_HISTORY -> answers.data(request, Map.of("revisions", history(file, args)));
                case Actions.ADMIN_SETTINGS_REVISION -> revision(request, file, args);
                default -> answers.refused(request, "dashboard.unsupported");
            };
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
    }

    private Payloads.AdminSettingsFile get(String file) {
        var nodeData = sources.nodeData().get();
        var configuration = sources.configuration();
        var latest = nodeData.latestSettings(file);
        var tree = latest.map(revision -> tree(revision.content()))
                .orElseGet(() -> configuration.currentTree(file).orElse(MissingNode.getInstance()));
        var status = configuration.status(nodeData).stream()
                .filter(entry -> entry.path().equals(file))
                .findFirst()
                .orElseThrow(() -> CommandFailure.of(INVALID));
        var overridden = Configuration.CONFIG_YML.path().toString().equals(file)
                ? configuration.nodeSettings().overrides().sharedKeys()
                : List.<String>of();
        var settings = SettingsCatalogue.fields(file).stream()
                .map(field -> setting(field, tree, overridden.contains(field.key())))
                .toList();
        return new Payloads.AdminSettingsFile(
                file, status.latest(), status.applied(), status.edited(), status.behind(), settings);
    }

    private Payloads.AdminSetting setting(SettingsCatalogue.Field field, JsonNode tree, boolean overridden) {
        var value = at(tree, field);
        if (field.secret()) {
            return new Payloads.AdminSetting(
                    field.section(),
                    field.key(),
                    field.type().name(),
                    field.choices(),
                    field.restart(),
                    null,
                    secret(value),
                    overridden);
        }
        return new Payloads.AdminSetting(
                field.section(),
                field.key(),
                field.type().name(),
                field.choices(),
                field.restart(),
                value.isMissingNode() ? null : value,
                null,
                overridden);
    }

    private Payloads.AdminSecret secret(JsonNode stored) {
        if (sources.configuration().discordSettings().urlFromEnvironment()) {
            return new Payloads.AdminSecret(true, "environment");
        }
        var set = stored.isString() && !stored.asString().isBlank();
        return new Payloads.AdminSecret(set, set ? "file" : "none");
    }

    private List<Payloads.AdminSettingsChange> history(String file, JsonNode args) {
        var page = Math.clamp(args.path("page").asInt(1), 1, Integer.MAX_VALUE / PAGE_SIZE);
        return sources.nodeData().get().settingsHistory(file, (page - 1) * PAGE_SIZE, PAGE_SIZE).stream()
                .map(this::change)
                .toList();
    }

    private Payloads.AdminSettingsChange change(SettingsChange change) {
        return new Payloads.AdminSettingsChange(
                change.revision(),
                change.actor(),
                change.node(),
                sources.instant().apply(change.changed()));
    }

    private ActionResult revision(ActionRequest request, String file, JsonNode args) {
        var number = args.path("revision").asInt(0);
        var nodeData = sources.nodeData().get();
        var stored = nodeData.settingsAt(file, number);
        var change = nodeData.settingsChange(file, number);
        if (stored.isEmpty() || change.isEmpty()) return answers.refused(request, UNKNOWN_REVISION);
        var after = tree(stored.get().content());
        var before = nodeData.settingsAt(file, number - 1)
                .map(previous -> tree(previous.content()))
                .orElse(MissingNode.getInstance());
        var entry = change(change.get());
        return answers.data(
                request,
                new Payloads.AdminSettingsRevision(
                        file, number, entry.actor(), entry.node(), entry.changed(), diff(file, before, after)));
    }

    /**
     * @param file   the file
     * @param before the content before
     * @param after  the content after
     * @return the settings that changed, each once, as the catalogue names them
     */
    static List<Payloads.AdminSettingDiff> diff(String file, JsonNode before, JsonNode after) {
        var keys = new LinkedHashSet<String>();
        for (var leaf : Configuration.changedKeys(before, after)) keys.add(owner(file, leaf));
        var diffs = new ArrayList<Payloads.AdminSettingDiff>();
        for (var key : keys) {
            var field = SettingsCatalogue.field(file, key);
            if (field.isEmpty() || field.get().secret()) {
                diffs.add(new Payloads.AdminSettingDiff(key, null, null, field.isPresent()));
                continue;
            }
            diffs.add(new Payloads.AdminSettingDiff(
                    key, present(at(before, field.get())), present(at(after, field.get())), false));
        }
        return diffs;
    }

    private static String owner(String file, String leaf) {
        var all = new ArrayList<SettingsCatalogue.Field>(SettingsCatalogue.fields(file));
        SettingsCatalogue.FILE_ONLY.stream()
                .filter(field -> field.file().equals(file))
                .forEach(all::add);
        return all.stream()
                .map(SettingsCatalogue.Field::key)
                .filter(key -> leaf.equals(key) || leaf.startsWith(key + "."))
                .findFirst()
                .orElse(leaf);
    }

    private static JsonNode at(JsonNode tree, SettingsCatalogue.Field field) {
        var node = tree;
        for (var part : field.path()) node = node.path(part);
        return node;
    }

    private static @Nullable JsonNode present(JsonNode node) {
        return node.isMissingNode() ? null : node;
    }

    private static JsonNode tree(String content) {
        try {
            return YAML.readTree(content);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }

    private static String file(JsonNode args) {
        var file = args.path("file").asString("").strip();
        return Configuration.SHARED.stream()
                .map(key -> key.path().toString())
                .filter(file::equals)
                .findFirst()
                .orElseThrow(() -> CommandFailure.of(INVALID));
    }

    /**
     * What the settings are read from.
     *
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions, once it exists
     * @param instant       turns a stored time into an instant
     * @param render        renders a refusal as plain text
     */
    public record Sources(
            Configuration configuration,
            Supplier<ANodeData> nodeData,
            Function<LocalDateTime, Instant> instant,
            Function<CommandFailure, String> render) {}
}
