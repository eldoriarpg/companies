/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.price.PriceSource;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The shop plugins Companies reads prices from. From their trades, listings and server prices it works out a baseline
 * price per material, which order prices fall back to while Companies' own orders are too few to tell. Companies
 * registers its own sources for the shop plugins it knows when they are present; another plugin can register a source
 * for its own shops, best in its {@code onEnable}. A source is dropped when the plugin that registered it is disabled.
 * Each server of a network reads the shops it runs itself.
 */
public interface PriceApi {
    /**
     * Registers a source. A source with the same id replaces the one registered before. It is handed its recorder at
     * once; the recorder drops the trades it is handed while the source is switched off in {@code prices.yml} or
     * replaced. The id is 1 to 64 letters, digits, {@code _}, {@code -} and {@code .}, compared ignoring case; a source
     * with another id is refused with a warning.
     *
     * @param plugin the plugin the source belongs to; it is dropped when the plugin is disabled
     * @param source the source
     */
    void register(Plugin plugin, PriceSource source);

    /**
     * Removes the source with the given id. Its recorder drops the trades it is handed from then on.
     *
     * @param id id of the source
     */
    void unregister(String id);

    /**
     * The sources in use.
     *
     * @return the ids of the registered sources that are not switched off, lower case, in the order they registered
     */
    List<String> sources();
}
