/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.area.AreaProvider;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The plot and claim plugins companies can own areas of. Companies registers its own providers for PlotSquared and
 * WorldGuard when they are present; another plugin can register a provider for its own areas, best in its
 * {@code onEnable}. A provider is dropped when the plugin that registered it is disabled. Areas of a provider that is
 * not registered stay stored and are shown as unavailable until it is registered again.
 */
public interface AreaApi {
    /**
     * Registers a provider. A provider with the same id replaces the one registered before; when the plugin of the new
     * provider is disabled, the id is gone until the old one registers again, which Companies' own do on a restart.
     *
     * @param plugin   the plugin the provider belongs to; it is dropped when the plugin is disabled
     * @param provider the provider
     */
    void register(Plugin plugin, AreaProvider provider);

    /**
     * Removes the provider with the given id.
     *
     * @param id id of the provider
     */
    void unregister(String id);

    /**
     * The providers in use.
     *
     * @return the ids of the registered providers that are not switched off, lower case, in the order they are asked
     *     which area holds a location
     */
    List<String> providers();
}
