/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The API of the plugin, registered as a Bukkit service.
 * <p>
 * Reads work from the start. Writes need the services built on the economy, which are handed over with
 * {@link #open(Writes)} once the server has started; until then they complete exceptionally with an
 * {@link ApiNotReadyException}. Every future completes on the given executor, never on the main thread.
 */
public final class CompaniesApiImpl implements CompaniesApi {
    private final WriteAccess writes = new WriteAccess();
    private final CompanyApi companies;
    private final OrderApi orders;
    private final PlayerApi players;
    private final RankingApi ranking;
    private final ServerEventApi events;

    /**
     * @param repositories the repositories the API reads from
     * @param server       what the API needs from the server
     * @param executor     completes the futures, off the main thread
     */
    public CompaniesApiImpl(Repositories repositories, Server server, Executor executor) {
        companies = new CompanyApiImpl(
                repositories.companies(),
                repositories.stats(),
                repositories.treasuries(),
                writes,
                server.players(),
                server.clock(),
                executor);
        orders = new OrderApiImpl(repositories.orders(), writes, server.players(), server.messages(), executor);
        players = new PlayerApiImpl(repositories.orders(), writes, executor);
        ranking = new RankingApiImpl(repositories.stats(), server.clock(), executor);
        events = new ServerEventApiImpl(repositories.serverEvents(), writes, server.clock(), executor);
    }

    /**
     * Creates the API of a plugin: players are looked up on its server and messages are rendered by its message
     * sender in the server's language.
     *
     * @param plugin       the plugin
     * @param repositories the repositories the API reads from
     * @param executor     completes the futures, off the main thread
     * @return the API, not registered yet
     */
    public static CompaniesApiImpl create(Plugin plugin, Repositories repositories, Executor executor) {
        var server = new Server(
                plugin.getServer()::getOfflinePlayer,
                failure -> failure.render(null, MessageSender.getPluginMessageSender(plugin)),
                Times::now);
        return new CompaniesApiImpl(repositories, server, executor);
    }

    /**
     * Registers the API with the services manager, so other plugins can load it.
     *
     * @param plugin plugin providing the API
     */
    public void register(Plugin plugin) {
        plugin.getServer().getServicesManager().register(CompaniesApi.class, this, plugin, ServicePriority.Normal);
    }

    /**
     * Refuses writes and removes the API from the services manager.
     *
     * @param plugin plugin providing the API
     */
    public void unregister(Plugin plugin) {
        writes.close();
        plugin.getServer().getServicesManager().unregister(CompaniesApi.class, this);
    }

    /**
     * Allows write operations.
     *
     * @param services the services of the write operations
     */
    public void open(Writes services) {
        writes.open(services);
    }

    @Override
    public CompanyApi companies() {
        return companies;
    }

    @Override
    public OrderApi orders() {
        return orders;
    }

    @Override
    public PlayerApi players() {
        return players;
    }

    @Override
    public RankingApi ranking() {
        return ranking;
    }

    @Override
    public ServerEventApi events() {
        return events;
    }

    /**
     * The repositories the API reads from.
     *
     * @param companies company repository
     * @param orders    order repository
     * @param stats      statistics repository
     * @param treasuries   treasury repository
     * @param serverEvents server event repository
     */
    public record Repositories(
            ACompanyData companies,
            AOrderData orders,
            AStatsData stats,
            ATreasuryData treasuries,
            AServerEventData serverEvents) {}

    /**
     * What the API needs from the server.
     *
     * @param players  looks up a player by id
     * @param messages renders the error of a rejected write as the player would see it
     * @param clock    the current time
     */
    public record Server(
            Function<UUID, OfflinePlayer> players,
            Function<CommandFailure, Component> messages,
            Supplier<LocalDateTime> clock) {}

    /**
     * The services of the write operations, which exist only once the economy is set up.
     *
     * @param orders      creates orders
     * @param memberships adds members to companies
     * @param itemReturns  stores granted items
     * @param serverEvents creates and ends server events
     */
    public record Writes(
            OrderCreationService orders,
            MembershipService memberships,
            ItemReturnService itemReturns,
            ServerEventService serverEvents) {}
}
