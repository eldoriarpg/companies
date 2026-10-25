/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.dashboard.BrandingSettings;
import de.eldoria.companies.configuration.elements.generalsettings.Hooks;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code overrides} section of {@code node.yml}: the few shared settings one server may set for itself, because
 * they depend on what that server has, such as its plugins. A setting left out uses the shared value. They are never
 * shared.
 * <p>
 * The section is read leniently: a value of the wrong type or an unknown key is named in {@link #problems()} and
 * ignored, so a typo never makes {@code node.yml} unreadable, which would reset the server's role and id.
 */
public final class NodeOverrides {
    /**
     * No overrides.
     */
    public static final NodeOverrides NONE = new NodeOverrides(Map.of(), List.of());

    private static final String MESSAGE_BLOCKING = "messageBlocking";
    private static final String BOARDS_ENABLED = "boardsEnabled";
    private static final String HOOKS = "hooks";
    private static final String BRANDING = "branding";
    private static final Set<String> HOOK_KEYS = Set.of("placeholderapi", "citizens", "fancynpcs");
    private static final Set<String> BRANDING_KEYS = Set.of("name", "icon");

    private final Map<String, Object> values;
    private final List<String> problems;

    private NodeOverrides(Map<String, Object> values, List<String> problems) {
        this.values = Map.copyOf(values);
        this.problems = List.copyOf(problems);
    }

    /**
     * @param section the {@code overrides} section as written, null when there is none
     * @return the overrides it sets
     */
    public static NodeOverrides parse(@Nullable JsonNode section) {
        if (section == null || section.isNull() || section.isMissingNode()) return NONE;
        var values = new HashMap<String, Object>();
        var problems = new ArrayList<String>();
        if (!section.isObject()) {
            problems.add("overrides must be a section of settings");
            return new NodeOverrides(values, problems);
        }
        for (var entry : section.properties()) {
            var key = entry.getKey();
            var value = entry.getValue();
            switch (key) {
                case MESSAGE_BLOCKING, BOARDS_ENABLED -> flag(key, value, values, problems);
                case HOOKS -> group(key, value, HOOK_KEYS, true, values, problems);
                case BRANDING -> group(key, value, BRANDING_KEYS, false, values, problems);
                default -> problems.add("overrides." + key + " is no setting a server can override");
            }
        }
        return new NodeOverrides(values, problems);
    }

    private static void group(
            String group,
            JsonNode section,
            Set<String> keys,
            boolean flags,
            Map<String, Object> values,
            List<String> problems) {
        if (section.isNull()) return;
        if (!section.isObject()) {
            problems.add("overrides." + group + " must be a section of settings");
            return;
        }
        for (var entry : section.properties()) {
            var key = group + "." + entry.getKey();
            if (!keys.contains(entry.getKey())) {
                problems.add("overrides." + key + " is no setting a server can override");
            } else if (flags) {
                flag(key, entry.getValue(), values, problems);
            } else {
                text(key, entry.getValue(), values, problems);
            }
        }
    }

    private static void flag(String key, JsonNode value, Map<String, Object> values, List<String> problems) {
        if (value.isNull()) return;
        if (value.isBoolean()) {
            values.put(key, value.booleanValue());
        } else {
            problems.add("overrides." + key + " must be true or false");
        }
    }

    private static void text(String key, JsonNode value, Map<String, Object> values, List<String> problems) {
        if (value.isNull()) return;
        if (value.isValueNode() && !value.isBinary()) {
            values.put(key, value.asString());
        } else {
            problems.add("overrides." + key + " must be a text");
        }
    }

    /**
     * @param shared {@code generalSettings.messageBlocking} of {@code config.yml}
     * @return the value on this server
     */
    public boolean messageBlocking(boolean shared) {
        return flag(MESSAGE_BLOCKING, shared);
    }

    /**
     * @param shared {@code boards.enabled} of {@code config.yml}
     * @return the value on this server
     */
    public boolean boardsEnabled(boolean shared) {
        return flag(BOARDS_ENABLED, shared);
    }

    /**
     * @param shared {@code generalSettings.hooks} of {@code config.yml}
     * @return the switches on this server
     */
    public Hooks hooks(Hooks shared) {
        return new Hooks(
                flag("hooks.placeholderapi", shared.placeholderapi()),
                flag("hooks.citizens", shared.citizens()),
                flag("hooks.fancynpcs", shared.fancynpcs()));
    }

    /**
     * @param shared {@code dashboard.branding} of {@code config.yml}
     * @return the name and icon of this server
     */
    public BrandingSettings branding(BrandingSettings shared) {
        return new BrandingSettings((String) values.getOrDefault("branding.name", shared.name()), (String)
                values.getOrDefault("branding.icon", shared.icon()));
    }

    /**
     * @return the keys below {@code overrides} this server sets, sorted
     */
    public List<String> set() {
        return values.keySet().stream().sorted().toList();
    }

    /**
     * @return the keys of {@code config.yml} this server sets for itself, sorted
     */
    public List<String> sharedKeys() {
        return set().stream().map(NodeOverrides::sharedKey).sorted().toList();
    }

    private static String sharedKey(String key) {
        return switch (key) {
            case MESSAGE_BLOCKING -> "generalSettings.messageBlocking";
            case BOARDS_ENABLED -> "boards.enabled";
            default -> key.startsWith(HOOKS + ".") ? "generalSettings." + key : "dashboard." + key;
        };
    }

    /**
     * @return what was ignored and why, empty when the section was read as written
     */
    public List<String> problems() {
        return problems;
    }

    private boolean flag(String key, boolean shared) {
        return (Boolean) values.getOrDefault(key, shared);
    }
}
