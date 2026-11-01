/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceSource;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.logging.Level;

/**
 * Registers Companies' own price sources for the shop plugins it ships hooks for. A source class is compiled against
 * its plugin, so it is only loaded by name and only when that plugin is enabled; a missing plugin can not cause a
 * {@link NoClassDefFoundError}. A source that fails to load, for example because the plugin's API changed, is skipped
 * with a warning. A source is registered in the name of its plugin, so it is dropped when that plugin is disabled, and
 * is switched off with {@code sources} in {@code prices.yml}.
 */
public final class PriceHooks {
    static final Map<String, String> KNOWN = Map.of();

    private PriceHooks() {}

    /**
     * Registers the sources of the enabled shop plugins.
     *
     * @param plugin  the plugin, whose class loader holds the hooks
     * @param sources the registry
     */
    public static void load(Plugin plugin, PriceSources sources) {
        load(plugin, sources, KNOWN);
    }

    /**
     * @param plugin  the plugin, whose class loader holds the hooks
     * @param sources the registry
     * @param known   the class name of the source of each shop plugin, by the plugin's name
     */
    static void load(Plugin plugin, PriceSources sources, Map<String, String> known) {
        var plugins = plugin.getServer().getPluginManager();
        known.forEach((name, className) -> {
            if (!plugins.isPluginEnabled(name)) return;
            try {
                var hook = Class.forName(className, true, PriceHooks.class.getClassLoader())
                        .getDeclaredConstructor()
                        .newInstance();
                var owner = plugins.getPlugin(name);
                sources.register(owner == null ? plugin : owner, (PriceSource) hook);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not hook into " + name + " for shop prices.", e);
            }
        });
    }
}
