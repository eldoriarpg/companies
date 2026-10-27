/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderRule;
import de.eldoria.companies.data.repository.ANodeData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads and saves the defined rules of {@code system-orders.yml} as a whole list, the way the dashboard's rules editor
 * edits them. Saving checks every rule first: an id that is set, at most {@value SystemOrderRule#MAX_ID_LENGTH}
 * characters, not {@value GuessedSettings#RULE} and not used by an earlier rule, and a material that names an item.
 * Then the list replaces the rules of the revision the admin saw and is stored as the next revision, like a settings
 * change; a revision stored meanwhile refuses the save and hands back its rules.
 */
public final class RulesEditor {
    private static final String FILE = Configuration.SYSTEM_ORDERS.path().toString();
    private static final String RULES = "rules";
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private RulesEditor() {}

    /**
     * The rules of the latest revision.
     *
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions
     * @return the revision and its rules
     */
    public static Rules read(Configuration configuration, ANodeData nodeData) {
        var latest = nodeData.settingsRevisions().getOrDefault(FILE, 0);
        return new Rules(latest, rules(tree(configuration, nodeData, latest)));
    }

    /**
     * Saves the rules as the next revision.
     *
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions
     * @param base          the revision the admin saw
     * @param rules         the rules, as a list of objects
     * @param knows         whether a material names an item that can be ordered
     * @param actor         who saved
     * @return what happened
     */
    public static Result save(
            Configuration configuration,
            ANodeData nodeData,
            int base,
            JsonNode rules,
            Predicate<String> knows,
            String actor) {
        var errors = problems(rules, knows);
        if (!errors.isEmpty()) return new Result.Invalid(errors);
        var latest = nodeData.settingsRevisions().getOrDefault(FILE, 0);
        if (base < 0 || base > latest) return new Result.Invalid(Map.of("", "invalid"));
        if (latest != base) return new Result.Newer(latest, rules(tree(configuration, nodeData, latest)));
        var tree = tree(configuration, nodeData, base);
        var edited = tree.isObject() ? ((ObjectNode) tree).deepCopy() : JsonNodeFactory.instance.objectNode();
        edited.set(RULES, rules.deepCopy());
        String content;
        try {
            content = SettingsEditor.normalized(
                    Configuration.SYSTEM_ORDERS, configuration, nodeData, YAML.writeValueAsString(edited));
        } catch (RuntimeException e) {
            return new Result.Invalid(Map.of(RULES, "invalid"));
        }
        var outcome = configuration.store(nodeData, FILE, base, content, actor).orElseThrow();
        if (outcome instanceof Configuration.StoreOutcome.Newer newer) {
            return new Result.Newer(newer.latest(), rules(tree(configuration, nodeData, newer.latest())));
        }
        return new Result.Done(outcome);
    }

    /**
     * What is wrong with the rules, by the dotted path of the value, such as {@code rules.2.id}.
     *
     * @param rules the rules
     * @param knows whether a material names an item that can be ordered
     * @return the problems, empty when there are none
     */
    static Map<String, String> problems(JsonNode rules, Predicate<String> knows) {
        var errors = new LinkedHashMap<String, String>();
        if (!rules.isArray()) {
            errors.put(RULES, "invalid");
            return errors;
        }
        var ids = new HashSet<String>();
        for (var index = 0; index < rules.size(); index++) {
            var rule = rules.get(index);
            var path = RULES + "." + index + ".";
            if (!rule.isObject()) {
                errors.put(RULES + "." + index, "invalid");
                continue;
            }
            var id = rule.path("id").asString("").trim();
            if (id.isEmpty()) errors.put(path + "id", "missing");
            else if (id.length() > SystemOrderRule.MAX_ID_LENGTH) errors.put(path + "id", "tooLong");
            else if (id.toLowerCase(Locale.ROOT).equals(GuessedSettings.RULE)) errors.put(path + "id", "reserved");
            else if (!ids.add(id)) errors.put(path + "id", "duplicate");
            var material = rule.path("material").asString("").trim();
            if (material.isEmpty() || !knows.test(material)) errors.put(path + "material", "unknown");
        }
        return errors;
    }

    private static JsonNode tree(Configuration configuration, ANodeData nodeData, int revision) {
        return nodeData.settingsAt(FILE, revision)
                .map(stored -> SettingsEditor.tree(stored.content()))
                .orElseGet(() -> configuration.currentTree(FILE).orElse(MissingNode.getInstance()));
    }

    private static JsonNode rules(JsonNode tree) {
        var rules = tree.path(RULES);
        return rules.isArray() ? rules : JsonNodeFactory.instance.arrayNode();
    }

    /**
     * The rules of a revision.
     *
     * @param revision the revision
     * @param rules    its rules, as a list of objects
     */
    public record Rules(int revision, JsonNode rules) {}

    /**
     * What came of saving rules.
     */
    public sealed interface Result {
        /**
         * Some rules were refused; nothing was stored.
         *
         * @param fields the problems by dotted path
         */
        record Invalid(Map<String, String> fields) implements Result {}

        /**
         * Another revision was stored after the one the admin saw; nothing was stored.
         *
         * @param latest the latest revision
         * @param rules  its rules
         */
        record Newer(int latest, JsonNode rules) implements Result {}

        /**
         * The rules were stored, or equalled the latest revision.
         *
         * @param outcome how the store went
         */
        record Done(Configuration.StoreOutcome outcome) implements Result {}
    }
}
