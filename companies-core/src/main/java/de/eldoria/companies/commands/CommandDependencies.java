/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.company.order.search.SearchSessions;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.updates.Updates;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Everything the commands need, handed to every command class through its constructor.
 * <p>
 * The command tree is registered while the plugin enables, but the economy and the services built on it exist only
 * from the post start on. Until {@link #provide(Services)} hands them in, {@link #services()} and
 * {@link #requireReady()} refuse with {@code error.notReady}; in the failsafe mode without an economy they keep
 * refusing. Every executor calls one of them first.
 */
public final class CommandDependencies {
    private final Plugin plugin;
    private final Configuration configuration;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final ANodeData nodeData;
    private final OrderDraftService drafts;
    private final SearchSessions searches = new SearchSessions();
    private volatile Optional<Services> services = Optional.empty();

    /**
     * @param plugin        the plugin
     * @param configuration configuration of the plugin
     * @param companyData   company repository
     * @param orderData     order repository
     * @param nodeData      node repository
     */
    public CommandDependencies(
            Plugin plugin,
            Configuration configuration,
            ACompanyData companyData,
            AOrderData orderData,
            ANodeData nodeData) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.companyData = companyData;
        this.orderData = orderData;
        this.nodeData = nodeData;
        this.drafts = new OrderDraftService(plugin, orderData, configuration);
    }

    /**
     * Hands in the services built in the post start, which lets the commands run.
     *
     * @param ready the services
     */
    public void provide(Services ready) {
        services = Optional.of(ready);
    }

    /**
     * Takes the services back, so the commands refuse again.
     */
    public void withdraw() {
        services = Optional.empty();
    }

    /**
     * @return the services built in the post start
     * @throws CommandFailure {@code error.notReady} while they do not exist
     */
    public Services services() {
        return services.orElseThrow(() -> CommandFailure.of("error.notReady"));
    }

    /**
     * Refuses commands while the services do not exist.
     *
     * @throws CommandFailure {@code error.notReady} while they do not exist
     */
    public void requireReady() {
        services();
    }

    /**
     * @return the plugin
     */
    public Plugin plugin() {
        return plugin;
    }

    /**
     * @return configuration of the plugin
     */
    public Configuration configuration() {
        return configuration;
    }

    /**
     * @return company repository
     */
    public ACompanyData companyData() {
        return companyData;
    }

    /**
     * @return order repository
     */
    public AOrderData orderData() {
        return orderData;
    }

    /**
     * @return node repository
     */
    public ANodeData nodeData() {
        return nodeData;
    }

    /**
     * @return the order drafts of the players
     */
    public OrderDraftService drafts() {
        return drafts;
    }

    /**
     * @return the order searches of the players
     */
    public SearchSessions searches() {
        return searches;
    }

    /**
     * @return the message sender of the plugin, styled by the configuration
     */
    public MessageSender messages() {
        return MessageSender.getPluginMessageSender(plugin);
    }

    /**
     * The services that exist only once the economy is known.
     *
     * @param economy             Vault economy
     * @param messageBlocker      blocker holding chat messages while a menu is open
     * @param itemReturnService   hands out returned items
     * @param levelService        recalculates company levels
     * @param notificationService notifies players, online or later
     * @param disbandService      dissolves companies
     * @param updates             update checks and downloads
     * @param deliveryService     delivers items to orders
     */
    public record Services(
            Economy economy,
            MessageBlocker messageBlocker,
            ItemReturnService itemReturnService,
            LevelService levelService,
            NotificationService notificationService,
            DisbandService disbandService,
            Updates updates,
            DeliveryService deliveryService) {}
}
