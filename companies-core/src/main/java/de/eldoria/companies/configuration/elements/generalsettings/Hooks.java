/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.generalsettings;

import java.util.Locale;

/**
 * Switches for the integrations with other plugins. A hook only loads when its plugin is enabled and its switch is on.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Hooks {
    private boolean placeholderapi = true;
    private boolean citizens = true;
    private boolean fancynpcs = true;

    public Hooks() {}

    public boolean placeholderapi() {
        return placeholderapi;
    }

    /**
     * @return whether NPCs of Citizens can be bound to boards
     */
    public boolean citizens() {
        return citizens;
    }

    /**
     * @return whether NPCs of FancyNpcs can be bound to boards
     */
    public boolean fancynpcs() {
        return fancynpcs;
    }

    /**
     * The switch of an NPC plugin's hook.
     *
     * @param pluginName name of the NPC plugin, like {@code Citizens}
     * @return whether the hook may load; false for a plugin without a switch
     */
    public boolean npc(String pluginName) {
        return switch (pluginName.toLowerCase(Locale.ROOT)) {
            case "citizens" -> citizens;
            case "fancynpcs" -> fancynpcs;
            default -> false;
        };
    }
}
