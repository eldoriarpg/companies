/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.boards.Boards;
import de.eldoria.companies.boards.npc.NpcBoards;
import de.eldoria.companies.commands.company.order.search.SearchSessions;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.EconomyData;
import de.eldoria.companies.data.repository.InformationData;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.nodes.DatabaseBus;
import de.eldoria.companies.nodes.NetworkPresence;
import de.eldoria.companies.nodes.NodeBus;
import de.eldoria.companies.nodes.NodeCaches;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.SoloOrderService;
import de.eldoria.companies.services.AdminOrderService;
import de.eldoria.companies.services.CompanyChanges;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.updates.Updates;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private final AMembershipData membershipData;
    private final InformationData information;
    private final EconomyData economy;
    private final OrderDraftService drafts;
    private final SearchSessions searches = new SearchSessions();
    private final Boards boards;
    private final NpcBoards npcBoards;
    private final List<Runnable> reloadHooks = new CopyOnWriteArrayList<>();
    private volatile NodeCaches caches = new NodeCaches(Logger.getLogger(CommandDependencies.class.getName()));
    private volatile NodeBus bus = new DatabaseBus();
    private volatile NetworkPresence presence = new NetworkPresence(Clock.systemUTC());
    private volatile Optional<Services> services = Optional.empty();

    /**
     * @param plugin        the plugin
     * @param configuration configuration of the plugin
     * @param companyData   company repository
     * @param orderData     order repository
     * @param nodeData       node repository
     * @param membershipData invite and application repository
     * @param information    inbox, payout ledger and history repositories
     * @param economy        treasury, repeating order and alert repositories
     */
    public CommandDependencies(
            Plugin plugin,
            Configuration configuration,
            ACompanyData companyData,
            AOrderData orderData,
            ANodeData nodeData,
            AMembershipData membershipData,
            InformationData information,
            EconomyData economy) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.companyData = companyData;
        this.orderData = orderData;
        this.nodeData = nodeData;
        this.membershipData = membershipData;
        this.information = information;
        this.economy = economy;
        this.drafts = new OrderDraftService(plugin, orderData, configuration);
        this.boards = Boards.create(() -> configuration.boardSettings(), orderData);
        this.npcBoards = new NpcBoards(
                plugin,
                boards,
                () -> configuration.boardBindings(),
                () -> configuration.saveBoardBindings(),
                (sender, failure) -> failure.sendTo(sender, messages()));
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
     * Hands in the bus of this node, what it knows about the network and the caches other nodes can invalidate,
     * before the commands are registered.
     *
     * @param bus      the bus of this node
     * @param presence where the players of the network are and which nodes were heard from
     * @param caches   the caches other nodes can invalidate
     */
    public void useNodes(NodeBus bus, NetworkPresence presence, NodeCaches caches) {
        this.bus = bus;
        this.presence = presence;
        this.caches = caches;
    }

    /**
     * @return the bus of this node, the {@link DatabaseBus} until {@link #useNodes} handed in another
     */
    public NodeBus bus() {
        return bus;
    }

    /**
     * @return where the players of the network are and which nodes were heard from
     */
    public NetworkPresence presence() {
        return presence;
    }

    /**
     * @return the caches of this node that other nodes can invalidate
     */
    public NodeCaches caches() {
        return caches;
    }

    /**
     * Runs the hook after every {@code /companyadmin reload}, once the configuration was read again.
     *
     * @param hook applies the reloaded configuration
     */
    public void onReload(Runnable hook) {
        reloadHooks.add(hook);
    }

    /**
     * Applies a reloaded configuration through every hook. A failing hook is logged and does not stop the others.
     */
    public void reloaded() {
        for (var hook : reloadHooks) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not apply the reloaded configuration.", e);
            }
        }
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
     * @return invite and application repository
     */
    public AMembershipData membershipData() {
        return membershipData;
    }

    /**
     * @return inbox, payout ledger and history repositories
     */
    public InformationData information() {
        return information;
    }

    /**
     * @return treasury, repeating order and alert repositories
     */
    public EconomyData economy() {
        return economy;
    }

    /**
     * @return the order drafts of the players
     */
    public OrderDraftService drafts() {
        return drafts;
    }

    /**
     * @return writes the history entries of ended orders
     */
    public HistoryRecorder history() {
        return new HistoryRecorder(information.history(), Clock.systemDefaultZone());
    }

    /**
     * @return ends orders and records their history
     * @throws CommandFailure {@code error.notReady} while the services do not exist
     */
    public OrderOutcomes outcomes() {
        return OrderOutcomes.create(
                plugin, orderData, companyData, history(), services().itemReturnService());
    }

    /**
     * @return fires the events of founding, renaming and handing over companies
     */
    public CompanyChanges companyChanges() {
        return CompanyChanges.create(plugin, companyData);
    }

    /**
     * @return accepts orders for companies
     */
    public OrderAcceptService orderAccept() {
        return new OrderAcceptService(
                orderData,
                configuration::companySettings,
                plugin.getServer().getPluginManager()::callEvent,
                Threading.VIRTUAL);
    }

    /**
     * @return the order actions of admins
     * @throws CommandFailure {@code error.notReady} while the services do not exist
     */
    public AdminOrderService adminOrders() {
        var ready = services();
        return AdminOrderService.create(
                plugin,
                orderData,
                companyData,
                ready.orderCompletion(),
                history(),
                ready.economy(),
                configuration,
                ready.notificationService(),
                ready.itemReturnService(),
                ready.series());
    }

    /**
     * @return writes admin actions to the server log
     */
    public AdminLog adminLog() {
        return new AdminLog(plugin.getLogger());
    }

    /**
     * @return the order searches of the players
     */
    public SearchSessions searches() {
        return searches;
    }

    /**
     * @return the order boards, opened by signs, NPCs and {@code /company board}
     */
    public Boards boards() {
        return boards;
    }

    /**
     * @return the NPCs bound to boards and the hooks of the NPC plugins
     */
    public NpcBoards npcBoards() {
        return npcBoards;
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
     * @param menus               shows menus as dialogs or in chat
     * @param membershipService   invites, applications and joining
     * @param orderCompletion     completes delivered orders
     * @param orderCreation       creates orders and charges their owners
     * @param treasury            moves money into and out of company treasuries
     * @param series              starts, stops and resumes repeating orders
     * @param soloOrders          claims and aborts the orders players work on alone
     */
    public record Services(
            Economy economy,
            MessageBlocker messageBlocker,
            ItemReturnService itemReturnService,
            LevelService levelService,
            NotificationService notificationService,
            DisbandService disbandService,
            Updates updates,
            DeliveryService deliveryService,
            MenuService menus,
            MembershipService membershipService,
            OrderCompletionService orderCompletion,
            OrderCreationService orderCreation,
            TreasuryService treasury,
            SeriesService series,
            SoloOrderService soloOrders) {}
}
