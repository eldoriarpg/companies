/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.treasury.Treasury;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Where the suggestions of {@code /company bank} read their data from: the treasury of a player's company, cached like
 * other database data.
 * <p>
 * The plugin hands the sources in when it enables and takes them back when it disables.
 */
public final class TreasurySources {
    private static volatile Optional<TreasurySources> current = Optional.empty();

    private final SuggestionCache<UUID, Optional<Treasury>> treasuries;

    /**
     * @param companyData  company repository
     * @param treasuryData treasury repository
     * @param executor     executor loading data from the database
     */
    public TreasurySources(ACompanyData companyData, ATreasuryData treasuryData, Executor executor) {
        this(new SuggestionCache<>(
                SuggestionSources.LIFETIME, executor, player -> treasury(companyData, treasuryData, player)));
    }

    /**
     * @param treasuries cache of the treasury of a player's company, by the player's id
     */
    public TreasurySources(SuggestionCache<UUID, Optional<Treasury>> treasuries) {
        this.treasuries = treasuries;
    }

    /**
     * @param sources the sources the providers read from
     */
    public static void use(TreasurySources sources) {
        current = Optional.of(sources);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * @return the sources, empty while the plugin is disabled
     */
    public static Optional<TreasurySources> current() {
        return current;
    }

    /**
     * Drops the cached treasury of a player's company, after the player changed it.
     *
     * @param player id of the player
     */
    public static void invalidate(UUID player) {
        current.ifPresent(sources -> sources.treasuries().invalidate(player));
    }

    /**
     * @return the treasury of a player's company, by the player's id
     */
    public SuggestionCache<UUID, Optional<Treasury>> treasuries() {
        return treasuries;
    }

    private static Optional<Treasury> treasury(ACompanyData companyData, ATreasuryData treasuryData, UUID player) {
        var company = companyData
                .retrievePlayerCompany(Bukkit.getOfflinePlayer(player))
                .asFuture()
                .join();
        if (company == null || company.isEmpty()) return Optional.empty();
        return Optional.of(treasuryData.retrieveTreasury(company.get().id()).join());
    }
}
