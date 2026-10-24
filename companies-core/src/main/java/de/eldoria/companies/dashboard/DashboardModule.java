/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.api.CompaniesApi;
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.dashboard.actions.CompanyWrites;
import de.eldoria.companies.dashboard.actions.DashboardWrites;
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
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderReceiveService;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.SavedItemService;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
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
        Function<UUID, String> names = uuid -> {
            var name = Bukkit.getOfflinePlayer(uuid).getName();
            return name == null ? uuid.toString() : name;
        };
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
                clock));
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
                        new NodeInfo(node, primary ? NodeRole.PRIMARY : NodeRole.SECONDARY),
                        userAgent,
                        DashboardReads.MAX_PAGE_SIZE),
                primary ? keys::refreshIfDue : keys::current,
                () -> DashboardReads.FEATURES.stream()
                        .filter(settings.get()::feature)
                        .toList(),
                new BrandingSource(
                        () -> settings.get().branding(),
                        () -> PlainText.of(plugin.getServer().motd()),
                        List.of(plugin.getDataFolder().toPath(), Path.of("").toAbsolutePath()),
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
            ItemServices items) {}

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
