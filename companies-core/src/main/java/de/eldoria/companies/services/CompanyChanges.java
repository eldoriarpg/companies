/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyCreateEvent;
import de.eldoria.companies.events.company.CompanyOwnerTransferEvent;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.company.PreCompanyCreateEvent;
import de.eldoria.companies.util.Threading;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fires the events of founding, renaming and handing over companies.
 * <p>
 * {@link PreCompanyCreateEvent} fires on the thread of the founding, before the founder is charged. The events after a
 * change load the company fresh on the executor, off the main thread, so they carry the changed company; a company
 * that is gone by then fires nothing.
 */
public final class CompanyChanges {
    private final ACompanyData companyData;
    private final Function<UUID, OfflinePlayer> players;
    private final Consumer<Event> events;
    private final Logger logger;
    private final Executor executor;

    /**
     * @param companyData company repository
     * @param players     looks up a player by id
     * @param events      fires the events
     * @param logger      logs events that could not be fired
     * @param executor    loads the changed companies off the main thread
     */
    public CompanyChanges(
            ACompanyData companyData,
            Function<UUID, OfflinePlayer> players,
            Consumer<Event> events,
            Logger logger,
            Executor executor) {
        this.companyData = companyData;
        this.players = players;
        this.events = events;
        this.logger = logger;
        this.executor = executor;
    }

    /**
     * The changes of a running plugin, firing the events through its plugin manager.
     *
     * @param plugin      the plugin
     * @param companyData company repository
     * @return the changes
     */
    public static CompanyChanges create(Plugin plugin, ACompanyData companyData) {
        return new CompanyChanges(
                companyData,
                plugin.getServer()::getOfflinePlayer,
                plugin.getServer().getPluginManager()::callEvent,
                plugin.getLogger(),
                Threading.VIRTUAL);
    }

    /**
     * Asks the integrations whether a player may found a company.
     *
     * @param player founder
     * @param name   name of the company
     * @param price  founding price
     * @return why the founding was refused, or empty when it may go on
     */
    public Optional<CommandFailure> mayFound(Player player, String name, double price) {
        var event = new PreCompanyCreateEvent(player, name, price);
        events.accept(event);
        if (!event.isCancelled()) return Optional.empty();
        return Optional.of(event.cancelReason()
                .map(reason -> CommandFailure.of("error.cancelledByIntegration", reason))
                .orElseGet(() -> CommandFailure.actionBar("error.cancelledByIntegration")));
    }

    /**
     * Fires {@link CompanyCreateEvent} for a founded company.
     *
     * @param company id of the new company
     * @param founder founder and owner
     * @return completes once the event was fired
     */
    public CompletableFuture<Void> founded(int company, OfflinePlayer founder) {
        return fire(company, profile -> new CompanyCreateEvent(profile, founder));
    }

    /**
     * Fires {@link CompanyRenameEvent} for a renamed company.
     *
     * @param company id of the company
     * @param oldName name before the rename
     * @param actor   who renamed it, or null for the console
     * @param byAdmin whether an admin renamed it
     * @return completes once the event was fired
     */
    public CompletableFuture<Void> renamed(int company, String oldName, @Nullable UUID actor, boolean byAdmin) {
        return fire(company, profile -> new CompanyRenameEvent(profile, oldName, actor, byAdmin));
    }

    /**
     * Fires {@link CompanyOwnerTransferEvent} for a company with a new owner.
     *
     * @param company       id of the company
     * @param previousOwner owner before the transfer
     * @param newOwner      owner now
     * @param byAdmin       whether an admin handed the company over
     * @return completes once the event was fired
     */
    public CompletableFuture<Void> ownerTransferred(int company, UUID previousOwner, UUID newOwner, boolean byAdmin) {
        return fire(
                company,
                profile -> new CompanyOwnerTransferEvent(
                        profile, players.apply(previousOwner), players.apply(newOwner), byAdmin));
    }

    private CompletableFuture<Void> fire(int company, Function<CompanyProfile, Event> event) {
        return CompletableFuture.runAsync(() -> profile(company).map(event).ifPresent(events), executor)
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not fire the event of company " + company, err);
                    return null;
                });
    }

    private Optional<CompanyProfile> profile(int company) {
        return Optional.ofNullable(companyData.retrieveCompanyById(company).join())
                .flatMap(found -> found)
                .flatMap(found -> Optional.ofNullable(
                                companyData.retrieveCompanyProfile(found).join())
                        .flatMap(profile -> profile));
    }
}
