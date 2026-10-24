/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.api.CompaniesApi;
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.dashboard.feed.FeedListener;
import de.eldoria.companies.dashboard.feed.PatchFeed;
import de.eldoria.companies.dashboard.gateway.BrandingSource;
import de.eldoria.companies.dashboard.gateway.GatewayStatus;
import de.eldoria.companies.dashboard.gateway.Handshake;
import de.eldoria.companies.dashboard.keys.DashboardKeys;
import de.eldoria.companies.dashboard.keys.HttpLynaKeys;
import de.eldoria.companies.dashboard.sessions.DashboardSessions;
import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.dashboard.views.PlainText;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds the dashboard of this node from the plugin's parts and registers its listeners.
 */
public final class DashboardModule {
    private static final long PERMISSION_REFRESH_TICKS = 20L * 60 * 5;
    private static final int ACTIONS_PER_WINDOW = 20;
    private static final Duration ACTION_WINDOW = Duration.ofSeconds(10);
    private static final Duration MARKET_LIFETIME = Duration.ofMinutes(1);

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
                ZoneId.systemDefault(),
                clock));
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
        var started = new AtomicReference<Dashboard>();
        var router = new DashboardRouter(new DashboardRouter.Parts(
                link,
                status,
                handshake,
                sessions,
                feed,
                views,
                reads,
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
        var listener = new DashboardListener(dashboard, () -> messages);
        plugin.getServer().getPluginManager().registerEvents(new FeedListener(feed), plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> dashboard.playersWithSessions().thenAccept(players -> Threading.mainThread(plugin)
                                .execute(() -> players.stream()
                                        .map(plugin.getServer()::getPlayer)
                                        .filter(Objects::nonNull)
                                        .forEach(listener::refresh))),
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
     * The plugin's repositories and services the dashboard reads.
     *
     * @param dashboard   the dashboard tables
     * @param api         the plugin's API
     * @param memberships invites and applications
     * @param alerts      order alerts
     * @param inbox       inboxes
     * @param treasury    treasuries
     * @param stats       statistics and rankings
     * @param search      the order search
     */
    public record Data(
            ADashboardData dashboard,
            CompaniesApi api,
            AMembershipData memberships,
            AAlertData alerts,
            AInboxData inbox,
            ATreasuryData treasury,
            AStatsData stats,
            SearchExecutor search) {}
}
