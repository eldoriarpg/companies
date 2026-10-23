/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code companies} PlaceholderAPI expansion. It answers from the caches and never waits for the database.
 */
public final class CompaniesExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final PlaceholderRegistry registry;

    /**
     * @param plugin   the plugin providing the expansion
     * @param registry resolves the ids
     */
    public CompaniesExpansion(Plugin plugin, PlaceholderRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "companies";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        return registry.resolve(player == null ? null : player.getUniqueId(), params);
    }
}
