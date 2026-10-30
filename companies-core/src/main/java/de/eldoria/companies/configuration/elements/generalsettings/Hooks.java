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
    private boolean worldguard = true;
    private boolean plotsquared = true;
    private boolean worldedit = true;

    public Hooks() {}

    /**
     * @param placeholderapi whether the placeholders are registered
     * @param citizens       whether NPCs of Citizens can be bound to boards
     * @param fancynpcs      whether NPCs of FancyNpcs can be bound to boards
     */
    public Hooks(boolean placeholderapi, boolean citizens, boolean fancynpcs) {
        this(placeholderapi, citizens, fancynpcs, true, true, true);
    }

    /**
     * @param placeholderapi whether the placeholders are registered
     * @param citizens       whether NPCs of Citizens can be bound to boards
     * @param fancynpcs      whether NPCs of FancyNpcs can be bound to boards
     * @param worldguard     whether jobs may lie in WorldGuard regions
     * @param plotsquared    whether jobs may lie on PlotSquared plots
     * @param worldedit      whether the area of a job can be taken from a WorldEdit selection
     */
    public Hooks(
            boolean placeholderapi,
            boolean citizens,
            boolean fancynpcs,
            boolean worldguard,
            boolean plotsquared,
            boolean worldedit) {
        this.placeholderapi = placeholderapi;
        this.citizens = citizens;
        this.fancynpcs = fancynpcs;
        this.worldguard = worldguard;
        this.plotsquared = plotsquared;
        this.worldedit = worldedit;
    }

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
     * @return whether jobs may lie in WorldGuard regions, which give companies access while they work
     */
    public boolean worldguard() {
        return worldguard;
    }

    /**
     * @return whether jobs may lie on PlotSquared plots, whose owners trust the working companies
     */
    public boolean plotsquared() {
        return plotsquared;
    }

    /**
     * @return whether the area of a job can be taken from a WorldEdit selection
     */
    public boolean worldedit() {
        return worldedit;
    }

    /**
     * The switch of a job hook.
     *
     * @param pluginName name of the plugin, like {@code WorldGuard}
     * @return whether the hook may load; false for a plugin without a switch
     */
    public boolean job(String pluginName) {
        return switch (pluginName.toLowerCase(Locale.ROOT)) {
            case "worldguard" -> worldguard;
            case "plotsquared" -> plotsquared;
            case "worldedit", "fastasyncworldedit" -> worldedit;
            default -> false;
        };
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
