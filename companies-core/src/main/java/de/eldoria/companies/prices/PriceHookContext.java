/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * What Companies' own price sources get when they are loaded: the plugin to register their listeners with, and the
 * settings of shop prices, read on every use.
 *
 * @param plugin   Companies
 * @param settings the settings of shop prices
 */
public record PriceHookContext(Plugin plugin, Supplier<PriceSettings> settings) {}
