/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.api.CompaniesApi;
import de.eldoria.companies.boards.Boards;
import de.eldoria.companies.boards.npc.NpcBoards;
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.RulesEditor;
import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.dashboard.actions.AdminEventWrites;
import de.eldoria.companies.dashboard.actions.AdminLevelWrites;
import de.eldoria.companies.dashboard.actions.AdminOfferWrites;
import de.eldoria.companies.dashboard.actions.AdminOrderWrites;
import de.eldoria.companies.dashboard.actions.AdminServerWrites;
import de.eldoria.companies.dashboard.actions.AdminSettingsWrites;
import de.eldoria.companies.dashboard.actions.AdminSupportWrites;
import de.eldoria.companies.dashboard.actions.AdminSystemOrderWrites;
import de.eldoria.companies.dashboard.actions.CompanyWrites;
import de.eldoria.companies.dashboard.actions.DashboardWrites;
import de.eldoria.companies.dashboard.actions.EventWrites;
import de.eldoria.companies.dashboard.actions.ItemWrites;
import de.eldoria.companies.dashboard.actions.OrderCreateWrites;
import de.eldoria.companies.dashboard.feed.FeedListener;
import de.eldoria.companies.dashboard.feed.PatchFeed;
import de.eldoria.companies.dashboard.gateway.BrandingSource;
import de.eldoria.companies.dashboard.gateway.GatewayStatus;
import de.eldoria.companies.dashboard.gateway.Handshake;
import de.eldoria.companies.dashboard.keys.DashboardKeys;
import de.eldoria.companies.dashboard.keys.HttpLynaKeys;
import de.eldoria.companies.dashboard.market.MarketHistory;
import de.eldoria.companies.dashboard.sessions.DashboardSessions;
import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.dashboard.views.OrderCatalogue;
import de.eldoria.companies.dashboard.views.PlainText;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderReceiveService;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.SavedItemService;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.offers.ContestService;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.services.AdminCompanyService;
import de.eldoria.companies.services.AdminGrantService;
import de.eldoria.companies.services.AdminOrderLookup;
import de.eldoria.companies.services.AdminOrderService;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelAdminService;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Builds the dashboard of this node from the plugin's parts and registers its listeners.
 */
public final class DashboardModule {
    private static final long PERMISSION_REFRESH_TICKS = 20L * 60 * 5;
    private static final int ACTIONS_PER_WINDOW = 20;
    private static final Duration ACTION_WINDOW = Duration.ofSeconds(10);
    private static final Duration MARKET_LIFETIME = Duration.ofMinutes(1);
    private static final Duration CATALOGUE_LIFETIME = Duration.ofMinutes(10);

    private DashboardModule() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Builds the dashboard and starts it.
     *
     * @param plugin        the plugin
     * @param configuration the configuration
     * @param data          the plugin's repositories the dashboard reads
     * @param keyChanged    tells the other nodes that a new key was stored
     * @param timer         runs the scheduled work
     * @return the started dashboard
     */
    public static Dashboard start(
            Plugin plugin,
            Configuration configuration,
            Data data,
            Runnable keyChanged,
            ScheduledExecutorService timer) {
        var logger = plugin.getLogger();
        var clock = Clock.systemUTC();
        var nodeSettings = configuration.nodeSettings();
        var node = nodeSettings.nodeUid();
        var primary = nodeSettings.nodeType() == NodeType.PRIMARY;
        var version = plugin.getPluginMeta().getVersion();
        var userAgent = "Companies/" + version;
        var messages = MessageSender.getPluginMessageSender(plugin);
        var localizer = ILocalizer.getPluginLocalizer(plugin);
        Function<UUID, String> names =
                uuid -> SystemOwner.name(uuid, id -> Bukkit.getOfflinePlayer(id).getName());
        Function<String, String> refusal =
                key -> PlainText.of(CommandFailure.of(key).render(null, messages));

        var keys = new DashboardKeys(
                data.dashboard(), new HttpLynaKeys(HttpLynaKeys.LYNA, userAgent), clock, logger, primary);
        var sessions = new DashboardSessions(data.dashboard(), configuration::dashboardSettings, node, clock);
        var status = new GatewayStatus(logger);
        var views = new Views(new Views.Sources(
                data.api(),
                data.stats(),
                data.memberships(),
                data.alerts(),
                data.inbox(),
                configuration::companySettings,
                configuration::orderSetting,
                names,
                ZoneOffset.UTC,
                clock,
                () -> configuration.systemOrderSettings().markSystemOrders(),
                data.offers().data()::marks));
        var market = new MarketHistory(new MarketHistory.Sources(
                data.dashboard(),
                data.history(),
                () -> configuration.orderSetting().market(),
                () -> configuration.generalSettings().historyDays(),
                Times::zone,
                clock,
                logger));
        var catalogue =
                new OrderCatalogue(data.orders().requests(), data.orders().savedItems(), data.history());
        var renderer = new NotificationRenderer(localizer::localize);
        var reads = new DashboardReads(
                views,
                new DashboardReads.Sources(
                        data.search()::search,
                        data.inbox(),
                        data.memberships(),
                        data.treasury(),
                        new Expiring<>(data.dashboard()::market, MARKET_LIFETIME, clock),
                        () -> configuration.orderSetting().market(),
                        new Expiring<>(market::changes, MARKET_LIFETIME, clock),
                        (material, days) -> market.points(
                                material,
                                days,
                                configuration.orderSetting().market().minOrders()),
                        market::today,
                        new Expiring<>(catalogue::catalogue, CATALOGUE_LIFETIME, clock),
                        catalogue::known,
                        entry -> inboxText(renderer, messages, entry),
                        refusal,
                        names,
                        clock));
        Supplier<DashboardSettings> settings = configuration::dashboardSettings;
        var handshake = new Handshake(
                new Handshake.Identity(
                        version,
                        schemaVersion(),
                        new NodeInfo(
                                node,
                                primary ? NodeRole.PRIMARY : NodeRole.SECONDARY,
                                nodeSettings.name().isEmpty() ? null : nodeSettings.name()),
                        userAgent,
                        DashboardReads.MAX_PAGE_SIZE),
                primary ? keys::refreshIfDue : keys::current,
                () -> DashboardReads.FEATURES.stream()
                        .filter(settings.get()::feature)
                        .filter(feature -> !feature.equals(Features.ORDERS_SERVER_MARKS)
                                || configuration.systemOrderSettings().markSystemOrders())
                        .toList(),
                new BrandingSource(
                        configuration::branding,
                        () -> PlainText.of(plugin.getServer().motd()),
                        List.of(plugin.getDataFolder().toPath(), Path.of("").toAbsolutePath()),
                        name -> Optional.ofNullable(data.admin().nodeData().get())
                                .flatMap(nodes -> nodes.asset(name)),
                        logger)::read);
        var link = new LinkProxy();
        var feed = new PatchFeed(
                views,
                link,
                node,
                Executors.newSingleThreadExecutor(
                        Thread.ofVirtual().name("Companies dashboard patches").factory()),
                logger);
        var company = new CompanyWrites(new CompanyWrites.Sources(
                data.companyOf(),
                Bukkit::getOfflinePlayer,
                data.company().members(),
                data.company().memberships(),
                data.company().profiles(),
                data.company().companies(),
                data.company().disband(),
                data.company().treasury(),
                data.company().currency(),
                failure -> PlainText.of(failure.render(null, messages)),
                text -> PlainText.of(messages.serializeError(
                        text.build(), text.replacements().toArray(TagResolver[]::new)))));
        var writes = new DashboardWrites(
                new DashboardWrites.Sources(
                        data.companyOf(),
                        data.orderAccept(),
                        data.companyOrders(),
                        data.ownerOrders(),
                        data.inboxService(),
                        failure -> PlainText.of(failure.render(null, messages)),
                        order -> feed.orderChanged(order, null),
                        player -> feed.inboxChanged(player),
                        clock),
                company,
                new ItemWrites(new ItemWrites.Sources(
                        uuid -> Optional.ofNullable(plugin.getServer().getPlayer(uuid)),
                        Threading.mainThread(plugin),
                        data.companyOf(),
                        data.items().orders(),
                        ItemWrites.Deliveries.of(data.items().delivery()),
                        data.items().receive(),
                        data.items().returns(),
                        failure -> PlainText.of(failure.render(null, messages)),
                        player -> Threading.mainThread(plugin)
                                .execute(() -> data.items().moved().accept(player)))),
                new OrderCreateWrites(new OrderCreateWrites.Sources(
                        Bukkit::getOfflinePlayer,
                        data.orders().creation(),
                        data.orders().series(),
                        catalogue,
                        data.orders().requests(),
                        data.orders().defaultLimit(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminOrderWrites(new AdminOrderWrites.Sources(
                        data.admin().lookup(),
                        data.admin().orders(),
                        name -> CompletableFuture.supplyAsync(
                                        () -> Optional.ofNullable(
                                                        plugin.getServer().getOfflinePlayerIfCached(name))
                                                .map(found -> new PlayerRef(
                                                        found.getUniqueId(), String.valueOf(found.getName()))),
                                        Threading.mainThread(plugin))
                                .join(),
                        data.admin().companyByName(),
                        uuid -> SystemOwner.name(
                                uuid,
                                id -> plugin.getServer().getOfflinePlayer(id).getName()),
                        views,
                        data.admin().log(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminSupportWrites(new AdminSupportWrites.Sources(
                        name -> CompletableFuture.supplyAsync(
                                        () -> Optional.ofNullable(
                                                        plugin.getServer().getOfflinePlayerIfCached(name))
                                                .map(found -> new PlayerRef(
                                                        found.getUniqueId(), String.valueOf(found.getName()))),
                                        Threading.mainThread(plugin))
                                .join(),
                        Bukkit::getOfflinePlayer,
                        uuid -> SystemOwner.name(
                                uuid,
                                id -> plugin.getServer().getOfflinePlayer(id).getName()),
                        data.admin().companies(),
                        data.admin().companyData(),
                        data.items().orders(),
                        data.inbox(),
                        entry -> inboxText(renderer, messages, entry),
                        data.admin().grants(),
                        data.company().treasury(),
                        data.treasury(),
                        data.company().disband(),
                        Threading.mainThread(plugin),
                        views,
                        data.admin().log(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminLevelWrites(new AdminLevelWrites.Sources(
                        data.admin().levels(),
                        data.admin().recalculate(),
                        Threading.mainThread(plugin),
                        data.admin().log(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminServerWrites(new AdminServerWrites.Sources(
                        data.admin().boards(),
                        data.admin().npcBoards(),
                        plugin.getServer().getConsoleSender(),
                        data.admin().nodeData(),
                        data.admin().bus(),
                        data.admin().syncConfiguration(),
                        views::instant,
                        Threading.mainThread(plugin),
                        data.admin().log(),
                        plugin.getLogger(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminSettingsWrites(new AdminSettingsWrites.Sources(
                        configuration,
                        data.admin().nodeData(),
                        data.admin().applyChanges(),
                        data.admin().log(),
                        plugin.getLogger(),
                        views::instant,
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminSystemOrderWrites(new AdminSystemOrderWrites.Sources(
                        data.items().orders(),
                        plugin.getServer().getOfflinePlayer(SystemOwner.ID),
                        configuration::systemOrderSettings,
                        views::instant,
                        Times::now,
                        data.admin().log(),
                        configuration,
                        data.admin().nodeData(),
                        data.admin().applyChanges(),
                        RulesEditor::knownMaterial,
                        plugin.getLogger(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new EventWrites(new EventWrites.Sources(
                        data.offers().data(),
                        data.offers().events(),
                        data.offers().contests(),
                        data.companyOf(),
                        views::instant,
                        Times::now,
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminOfferWrites(new AdminOfferWrites.Sources(
                        data.offers().data(),
                        data.offers().funding(),
                        data.offers().checkSoon(),
                        names,
                        views::instant,
                        Times::now,
                        data.admin().log(),
                        plugin.getLogger(),
                        failure -> PlainText.of(failure.render(null, messages)))),
                new AdminEventWrites(new AdminEventWrites.Sources(
                        data.offers().events(),
                        data.offers().data(),
                        data.offers().eventService(),
                        Times::zone,
                        data.offers().collector(),
                        views::instant,
                        Times::now,
                        data.admin().log(),
                        failure -> PlainText.of(failure.render(null, messages)))));
        var presence = new PresenceReporter(link::send, sessions, node, Threading.VIRTUAL, logger);
        presence.online(plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList());
        var started = new AtomicReference<Dashboard>();
        var router = new DashboardRouter(new DashboardRouter.Parts(
                link,
                status,
                handshake,
                sessions,
                feed,
                views,
                reads,
                writes,
                new ActionLimit(ACTIONS_PER_WINDOW, ACTION_WINDOW, clock),
                feature -> settings.get().feature(feature),
                refusal,
                (player, device) -> Optional.ofNullable(plugin.getServer().getPlayer(player))
                        .ifPresent(online -> messages.sendMessage(
                                online,
                                "dashboard.opened",
                                Values.text("device", device.orElse(localizer.localize("dashboard.unknownDevice"))))),
                () -> {
                    var current = started.get();
                    if (current != null) current.disconnect();
                },
                node,
                presence::reportAll,
                () -> {
                    var current = started.get();
                    if (current != null) current.welcomed();
                },
                Threading.VIRTUAL,
                logger));
        var dashboard = new Dashboard(new Dashboard.Parts(
                settings,
                () -> configuration.nodeSettings().dashboardKey(),
                keys,
                sessions,
                status,
                handshake,
                feed,
                market::record,
                router::accept,
                keyChanged,
                node,
                primary,
                userAgent,
                Threading.VIRTUAL,
                clock,
                logger));
        link.use(dashboard);
        started.set(dashboard);
        var listener = new DashboardListener(dashboard, () -> messages, presence);
        plugin.getServer().getPluginManager().registerEvents(new FeedListener(feed), plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            presence.reportAll();
                            dashboard.playersWithSessions().thenAccept(players -> Threading.mainThread(plugin)
                                    .execute(() -> players.stream()
                                            .map(plugin.getServer()::getPlayer)
                                            .filter(Objects::nonNull)
                                            .forEach(listener::refresh)));
                        },
                        PERMISSION_REFRESH_TICKS,
                        PERMISSION_REFRESH_TICKS);
        dashboard.start(timer);
        return dashboard;
    }

    private static String inboxText(NotificationRenderer renderer, MessageSender messages, InboxEntry entry) {
        var text = renderer.body(entry.type(), entry.args());
        return PlainText.of(
                messages.serializeMessage(text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    /**
     * The database schema version this plugin version brings, e.g. {@code 1.11}.
     *
     * @return the version
     */
    static String schemaVersion() {
        try {
            var version = SqlVersion.load(DashboardModule.class.getClassLoader());
            return version.major() + "." + version.patch();
        } catch (IOException e) {
            return "unknown";
        }
    }

    /**
     * The plugin's repositories and services the dashboard reads and acts through.
     *
     * @param dashboard     the dashboard tables
     * @param api           the plugin's API
     * @param memberships   invites and applications
     * @param alerts        order alerts
     * @param inbox         inboxes
     * @param treasury      treasuries
     * @param stats         statistics and rankings
     * @param history       the order history the market history is computed from
     * @param search        the order search
     * @param companyOf     the company a player is a member of
     * @param orderAccept   accepts orders for a company
     * @param companyOrders aborts a company's orders
     * @param ownerOrders   cancels, extends and raises own orders
     * @param inboxService  reads, clears and mutes inboxes
     * @param company       the services acting on a company
     * @param orders        the services placing orders
     * @param items         the services moving items with the player's inventory
     * @param admin         the admin tools
     * @param offers        the services of the event offers
     */
    public record Data(
            ADashboardData dashboard,
            CompaniesApi api,
            AMembershipData memberships,
            AAlertData alerts,
            AInboxData inbox,
            ATreasuryData treasury,
            AStatsData stats,
            AHistoryData history,
            SearchExecutor search,
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            OrderAcceptService orderAccept,
            CompanyOrderService companyOrders,
            OwnerOrderService ownerOrders,
            Inbox inboxService,
            CompanyServices company,
            OrderServices orders,
            ItemServices items,
            AdminServices admin,
            OfferServices offers) {}

    /**
     * The services behind the Events page and the admin tools for event offers and server events.
     *
     * @param data         the offer repository
     * @param contests     lets companies join and leave contests
     * @param funding      gives a failed prize back to the account that paid it
     * @param checkSoon    asks the primary to place and withdraw the chunks of goals soon
     * @param events       the server event repository
     * @param eventService creates and changes server events as {@code /companyadmin event} does
     * @param collector    the collector account an event paid from an account uses when none is named
     */
    public record OfferServices(
            AOfferData data,
            ContestService contests,
            SystemOrderService.Funding funding,
            Runnable checkSoon,
            AServerEventData events,
            ServerEventService eventService,
            Supplier<String> collector) {}

    /**
     * The services behind the dashboard's admin tools.
     *
     * @param orders changes any order as an admin
     * @param lookup looks orders up for admins
     * @param log    the admin log
     * @param companyByName a company by its name
     * @param companies     renames companies and changes their owner as an admin
     * @param companyData   the company repository
     * @param grants        grants players money and items
     * @param levels        changes the company levels
     * @param recalculate   recalculates the level of every company, then runs the given task
     * @param boards        the order boards of this node
     * @param npcBoards     the NPCs bound to boards on this node
     * @param nodeData      the node repository
     * @param bus           how a node exchanges messages, as the admin reads it
     * @param syncConfiguration does what {@code /companyadmin syncconfiguration} does; blocks until synced
     * @param applyChanges  applies the shared settings changed on this node a few seconds later on the main thread
     */
    public record AdminServices(
            AdminOrderService orders,
            AdminOrderLookup lookup,
            AdminLog log,
            Function<String, CompletableFuture<Optional<SimpleCompany>>> companyByName,
            AdminCompanyService companies,
            ACompanyData companyData,
            AdminGrantService grants,
            LevelAdminService levels,
            Consumer<Runnable> recalculate,
            Boards boards,
            NpcBoards npcBoards,
            Supplier<ANodeData> nodeData,
            Function<Node, String> bus,
            Consumer<String> syncConfiguration,
            Runnable applyChanges) {}

    /**
     * The services the dashboard moves items through, with the inventory of a player online on this node.
     *
     * @param orders   the order repository
     * @param delivery delivers to company orders
     * @param receive  receives delivered own orders
     * @param returns  hands out waiting items
     * @param moved    what the commands do after a delivery or a receipt, on the main thread
     */
    public record ItemServices(
            AOrderData orders,
            DeliveryService delivery,
            OrderReceiveService receive,
            ItemReturnService returns,
            Consumer<Player> moved) {}

    /**
     * The services the dashboard places orders through.
     *
     * @param creation     creates and prices orders
     * @param series       starts repeating orders
     * @param savedItems   the players' saved items
     * @param requests     turns an item into what an order line requests, empty when it cannot be ordered
     * @param defaultLimit the order limit of players without a limit permission
     */
    public record OrderServices(
            OrderCreationService creation,
            SeriesService series,
            SavedItemService savedItems,
            Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests,
            IntSupplier defaultLimit) {}

    /**
     * The services the dashboard acts on a company through.
     *
     * @param members     kicks, lets leave and edits permissions
     * @param memberships invites, applications and the join mode
     * @param profiles    edits the profile
     * @param companies   founds and renames
     * @param disband     dissolves companies
     * @param treasury    the treasuries
     * @param currency    the name of the currency
     */
    public record CompanyServices(
            MemberService members,
            MembershipService memberships,
            ProfileService profiles,
            CompanyService companies,
            DisbandService disband,
            TreasuryService treasury,
            Supplier<String> currency) {}
}
