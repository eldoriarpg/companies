/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderRule;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.orders.criteria.CriteriaRules;
import de.eldoria.companies.orders.criteria.CriteriaSyntax;
import org.bukkit.Material;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads and saves the defined rules of {@code system-orders.yml} as a whole list, the way the dashboard's rules editor
 * edits them. Saving checks every rule first: an id that is set, at most {@value SystemOrderRule#MAX_ID_LENGTH}
 * characters, not {@value GuessedSettings#RULE} and not used by an earlier rule, and what it orders: either a material
 * that names an item, or {@code items} whose entries each set exactly one of a known {@code material}, readable and
 * usable {@code criteria} or an {@code item}, with a known {@code resolve} and {@code pick}.
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
            var pick = rule.path("pick");
            if (!pick.isMissingNode()
                    && !pick.isNull()
                    && !names(SystemOrderRule.Pick.values()).contains(pick.asString(""))) {
                errors.put(path + "pick", "invalid");
            }
            var items = rule.path("items");
            if (items.isArray() && !items.isEmpty()) {
                for (var entry = 0; entry < items.size(); entry++) {
                    var problem = entryProblem(items.get(entry), knows);
                    if (problem.isPresent()) errors.put(path + "items." + entry, problem.get());
                }
                continue;
            }
            var material = rule.path("material").asString("").trim();
            if (material.isEmpty() || !knows.test(material)) errors.put(path + "material", "unknown");
        }
        return errors;
    }

    private static Optional<String> entryProblem(JsonNode entry, Predicate<String> knows) {
        if (!entry.isObject()) return Optional.of("invalid");
        var material = entry.path("material").asString("").trim();
        var criteria = entry.path("criteria").asString("").trim();
        var item = entry.path("item");
        var hasItem = !item.isMissingNode() && !item.isNull();
        var set = (material.isEmpty() ? 0 : 1) + (criteria.isEmpty() ? 0 : 1) + (hasItem ? 1 : 0);
        if (set != 1) return Optional.of("invalid");
        var resolve = entry.path("resolve");
        if (!resolve.isMissingNode()
                && !resolve.isNull()
                && !names(RuleEntry.Resolve.values()).contains(resolve.asString(""))) {
            return Optional.of("invalid");
        }
        if (!material.isEmpty()) return knows.test(material) ? Optional.empty() : Optional.of("unknown");
        if (criteria.isEmpty()) return Optional.empty();
        try {
            return CriteriaRules.problem(CriteriaSyntax.parse(criteria)).map(problem -> "unknown");
        } catch (CommandFailure failure) {
            return Optional.of("invalid");
        }
    }

    private static Set<String> names(Enum<?>[] values) {
        var names = new HashSet<String>();
        for (var value : values) names.add(value.name());
        return names;
    }

    /**
     * Adds an entry to a rule and saves the rules as the next revision. A rule that still names a single
     * {@code material} gets it as its first entry.
     *
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions
     * @param rule          the id of the rule
     * @param entry         the entry, as an object of {@code items}
     * @param knows         whether a material names an item that can be ordered
     * @param actor         who saved
     * @return what happened; an unknown rule is refused as invalid at {@code id}
     */
    public static Result addEntry(
            Configuration configuration,
            ANodeData nodeData,
            String rule,
            JsonNode entry,
            Predicate<String> knows,
            String actor) {
        var current = read(configuration, nodeData);
        var rules = current.rules().deepCopy();
        ObjectNode target = null;
        for (var candidate : rules) {
            if (candidate instanceof ObjectNode object
                    && object.path("id").asString("").trim().equals(rule)) {
                target = object;
            }
        }
        if (target == null) return new Result.Invalid(Map.of("id", "unknown"));
        var items = target.path("items").isArray()
                ? (ArrayNode) target.path("items")
                : JsonNodeFactory.instance.arrayNode();
        if (items.isEmpty()) {
            var material = target.path("material").asString("").trim();
            if (!material.isEmpty())
                items.add(JsonNodeFactory.instance.objectNode().put("material", material));
        }
        items.add(entry);
        target.set("items", items);
        target.remove("material");
        return save(configuration, nodeData, current.revision(), rules, knows, actor);
    }

    /**
     * Whether a material name names an item that can be ordered, as rules write it.
     *
     * @param material the name, such as {@code IRON_INGOT} or {@code iron_ingot}
     * @return true for an item
     */
    public static boolean knownMaterial(String material) {
        var found = Material.matchMaterial(material);
        return found != null && found.isItem() && !found.isAir();
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
