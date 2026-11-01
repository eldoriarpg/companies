/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.prices.PriceSources;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Registers price sources with the plugin's registry.
 */
final class PriceApiImpl implements PriceApi {
    private final PriceSources sources;

    PriceApiImpl(PriceSources sources) {
        this.sources = sources;
    }

    @Override
    public void register(Plugin plugin, PriceSource source) {
        sources.register(plugin, source);
    }

    @Override
    public void unregister(String id) {
        sources.unregister(id);
    }

    @Override
    public List<String> sources() {
        return sources.sources();
    }
}
