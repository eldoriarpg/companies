/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.AreaProvider;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Registers Companies' own area providers for the plot and claim plugins it ships hooks for: PlotSquared, WorldGuard
 * and GriefPrevention. PlotSquared and WorldGuard also serve jobs and have a switch in {@code config.yml}; the claim
 * plugins load whenever they are enabled and are switched off with {@code providers} in {@code areas.yml}. A provider
 * class is compiled against its plugin, so it is only loaded by name, and only when that plugin
 * is enabled and its hook is switched on; a missing plugin can not cause a {@link NoClassDefFoundError}. A provider
 * that fails to load, for example because the plugin's API changed, is skipped with a warning. A provider is registered
 * in the name of its plugin, so it is dropped when that plugin is disabled.
 */
public final class AreaHooks {
    static final Map<String, String> KNOWN = known();
    static final Set<String> SWITCHED = Set.of("PlotSquared", "WorldGuard");

    private AreaHooks() {}

    private static Map<String, String> known() {
        var known = new LinkedHashMap<String, String>();
        known.put("PlotSquared", "de.eldoria.companies.hooks.plotsquared.PlotSquaredAreas");
        known.put("WorldGuard", "de.eldoria.companies.hooks.worldguard.WorldGuardAreas");
        known.put("GriefPrevention", "de.eldoria.companies.hooks.griefprevention.GriefPreventionAreas");
        return known;
    }

    /**
     * Registers the providers of the enabled plugins.
     *
     * @param plugin     the plugin, whose class loader holds the hooks
     * @param providers  the registry
     * @param switchedOn whether the hook of a plugin with a switch in {@code config.yml} is on, by the plugin's name
     */
    public static void load(Plugin plugin, AreaProviders providers, Predicate<String> switchedOn) {
        var plugins = plugin.getServer().getPluginManager();
        KNOWN.forEach((name, className) -> {
            if (!plugins.isPluginEnabled(name)) return;
            if (SWITCHED.contains(name) && !switchedOn.test(name)) return;
            try {
                var hook = Class.forName(className, true, AreaHooks.class.getClassLoader())
                        .getDeclaredConstructor()
                        .newInstance();
                var owner = plugins.getPlugin(name);
                providers.register(owner == null ? plugin : owner, (AreaProvider) hook);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not hook into " + name + " for company areas.", e);
            }
        });
    }
}
