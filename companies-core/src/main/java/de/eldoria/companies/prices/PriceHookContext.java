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
 * What Companies' own price sources get when they are loaded: the plugin to register their listeners with, the
 * settings of shop prices, read on every use, and whether this server is the primary of its network.
 *
 * @param plugin   Companies
 * @param settings the settings of shop prices
 * @param primary  whether this server is the primary, which samples shops every server of the network shares
 */
public record PriceHookContext(Plugin plugin, Supplier<PriceSettings> settings, boolean primary) {}
