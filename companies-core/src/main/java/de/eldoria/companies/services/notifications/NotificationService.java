/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Sends notifications and turns the plugin's events into notifications.
 * <p>
 * Every notification is written to the inbox, whether its recipient is online or not:
 * <ul>
 *     <li>a muted category is stored delivered but unread: never shown live, not in the join summary, unread in the
 *     inbox;</li>
 *     <li>a recipient online on this server sees it live, which counts as read;</li>
 *     <li>otherwise it is stored pending. The {@link InboxPoller} of the server the recipient is on shows it, or their
 *     next join summary counts it.</li>
 * </ul>
 * Whether the recipient is online is decided on the main thread, where it is shown as well.
 */
public class NotificationService implements Listener {
    private static final String INBOX_COMMAND = "/company inbox";
    private static final String OPEN_KEY = "inbox_open";

    private final Plugin plugin;
    private final AInboxData inboxData;
    private final AOrderData orderData;
    private final ScheduledExecutorService workerPool;
    private final Executor mainThread;
    private final MessageSender sender;
    private final NotificationRenderer renderer;
    private final LiveDisplay display;
    private final Inbox inbox;

    @Nullable
    private final Economy economy;

    /**
     * @param plugin     the plugin
     * @param inboxData  the stored notifications
     * @param orderData  orders, for the delivered orders reminded of on join
     * @param workerPool runs the join tasks
     * @param settings   current notification settings
     * @param display    shows notifications live
     * @param mainThread runs tasks on the main thread
     */
    public NotificationService(
            Plugin plugin,
            AInboxData inboxData,
            AOrderData orderData,
            ScheduledExecutorService workerPool,
            Supplier<NotificationSettings> settings,
            LiveDisplay display,
            Executor mainThread) {
        this.plugin = plugin;
        this.inboxData = inboxData;
        this.orderData = orderData;
        this.workerPool = workerPool;
        this.mainThread = mainThread;
        this.display = display;
        sender = MessageSender.getPluginMessageSender(plugin);
        renderer = renderer(plugin);
        inbox = new Inbox(inboxData, settings);
        economy = plugin.getServer().getServicesManager().load(Economy.class);
    }

    /**
     * The service of the running server: live notifications in chat, on the action bar and with a sound.
     *
     * @param plugin     the plugin
     * @param inboxData  the stored notifications
     * @param orderData  orders, for the delivered orders reminded of on join
     * @param workerPool runs the join tasks
     * @param settings   current notification settings
     * @return the service
     */
    public static NotificationService create(
            Plugin plugin,
            AInboxData inboxData,
            AOrderData orderData,
            ScheduledExecutorService workerPool,
            Supplier<NotificationSettings> settings) {
        var display = LiveDisplay.paper(
                MessageSender.getPluginMessageSender(plugin), renderer(plugin), settings, plugin.getLogger());
        return new NotificationService(
                plugin, inboxData, orderData, workerPool, settings, display, Threading.mainThread(plugin));
    }

    private static NotificationRenderer renderer(Plugin plugin) {
        var localizer = ILocalizer.getPluginLocalizer(plugin);
        return new NotificationRenderer(localizer::localize);
    }

    /**
     * @return the player's side of the inbox
     */
    public Inbox inbox() {
        return inbox;
    }

    /**
     * @return turns notifications into texts
     */
    public NotificationRenderer renderer() {
        return renderer;
    }

    /**
     * Sends a notification.
     *
     * @param recipient player to notify
     * @param type      kind of notification
     * @param args      values for the placeholders of the type's message and action
     * @return completes once the notification is stored
     */
    public CompletableFuture<Void> send(UUID recipient, NotificationType type, NotificationArgs args) {
        var now = LocalDateTime.now();
        return inboxData
                .retrieveMuted(recipient)
                .thenCompose(mask -> type.category().mutedBy(mask)
                        ? store(recipient, type, args, now, InboxStatus.DELIVERED)
                        : CompletableFuture.supplyAsync(() -> showIfOnline(recipient, type, args), mainThread)
                                .thenCompose(shown -> store(
                                        recipient, type, args, now, shown ? InboxStatus.READ : InboxStatus.PENDING)))
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not send a notification", err);
                    return null;
                });
    }

    /**
     * Sends a notification to every member of a company.
     *
     * @param company company to notify
     * @param type    kind of notification
     * @param args    values of the notification
     * @param exclude members who are left out
     */
    public void sendCompany(
            ICompanyProfile company, NotificationType type, NotificationArgs args, Predicate<ICompanyMember> exclude) {
        for (var member : company.members()) {
            if (exclude.test(member)) continue;
            send(member.uuid(), type, args);
        }
    }

    /**
     * Sends a notification to every member of a company.
     *
     * @param company company to notify
     * @param type    kind of notification
     * @param args    values of the notification
     */
    public void sendCompany(ICompanyProfile company, NotificationType type, NotificationArgs args) {
        sendCompany(company, type, args, member -> false);
    }

    private CompletableFuture<Void> store(
            UUID recipient, NotificationType type, NotificationArgs args, LocalDateTime now, InboxStatus status) {
        return inboxData.submit(recipient, type, args, now, status).thenAccept(id -> {
            if (id.isEmpty()) plugin.getLogger().warning("A notification could not be stored for " + recipient);
        });
    }

    /**
     * Shows a notification live when its recipient is online on this server. Runs on the main thread.
     *
     * @param recipient player to show it to
     * @param type      kind of notification
     * @param args      values of the notification
     * @return true when it was shown
     */
    boolean showIfOnline(UUID recipient, NotificationType type, NotificationArgs args) {
        var player = plugin.getServer().getPlayer(recipient);
        if (player == null) return false;
        display.show(player, type, args);
        return true;
    }

    /**
     * The players online on this server, read on the main thread.
     *
     * @return their ids
     */
    CompletableFuture<List<UUID>> onlineHere() {
        return CompletableFuture.supplyAsync(
                () -> plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getUniqueId)
                        .toList(),
                mainThread);
    }

    /**
     * Shows a notification on the main thread when its recipient is still online here.
     *
     * @param recipient player to show it to
     * @param type      kind of notification
     * @param args      values of the notification
     */
    void showLater(UUID recipient, NotificationType type, NotificationArgs args) {
        mainThread.execute(() -> showIfOnline(recipient, type, args));
    }

    /**
     * Tells a player how many unread notifications wait in the inbox. Pending ones count as delivered from now on, so
     * no other server shows them live any more; muted categories are not counted.
     *
     * @param player player who joined
     * @return number of unread notifications counted
     */
    public CompletableFuture<Integer> summarize(Player player) {
        var uuid = player.getUniqueId();
        return inboxData
                .submitDelivered(uuid)
                .thenCompose(marked -> inboxData.retrieveMuted(uuid))
                .thenCompose(mask -> inboxData.retrieveUnreadCount(uuid, mask))
                .thenApply(count -> {
                    if (count > 0) sender.sendMessage(player, summary(count));
                    return count;
                });
    }

    /**
     * The join summary.
     *
     * @param count number of unread notifications
     * @return the summary with a button opening the inbox
     */
    static MenuText summary(int count) {
        return MenuText.create()
                .text("<neutral>")
                .locale("inbox.summary", Values.number("count", count))
                .space()
                .text("<" + OPEN_KEY + "><add>[", Values.click(OPEN_KEY, ClickEvent.runCommand(INBOX_COMMAND)))
                .locale("words.open")
                .text("]</" + OPEN_KEY + ">");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        workerPool.schedule(
                () -> {
                    summarize(player).join();
                    sendDeliveredOrders(player);
                },
                1L,
                TimeUnit.SECONDS);
    }

    private void sendDeliveredOrders(Player player) {
        var orders = orderData
                .retrieveOrdersByPlayer(player, OrderState.DELIVERED, OrderState.DELIVERED)
                .join();
        if (orders == null) return;
        for (var order : orders) {
            sender.sendMessage(player, renderer.message(NotificationType.MY_ORDER_DONE, orderArgs(order)));
        }
    }

    @EventHandler
    public void onCompanyDisband(CompanyDisbandEvent event) {
        sendCompany(event.company(), NotificationType.COMPANY_DISBANDED, NotificationArgs.create());
    }

    /**
     * Announces a member leaving the company.
     * <p>
     * Kicks share the handler list of leaves and are announced by {@link #onCompanyKick(CompanyKickEvent)} instead.
     *
     * @param event leave event
     */
    @EventHandler
    public void onCompanyLeaveEvent(CompanyLeaveEvent event) {
        if (event instanceof CompanyKickEvent) return;
        var leaver = event.player().getUniqueId();
        sendCompany(
                event.company(),
                NotificationType.MEMBER_LEFT,
                playerArgs(event.player().getName()),
                member -> member.uuid().equals(leaver));
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        var joined = event.player().getUniqueId();
        sendCompany(
                event.company(),
                NotificationType.MEMBER_JOINED,
                playerArgs(event.player().getName()),
                member -> member.uuid().equals(joined));
    }

    @EventHandler
    public void onCompanyKick(CompanyKickEvent event) {
        var kicked = event.player().getUniqueId();
        send(kicked, NotificationType.KICKED, NotificationArgs.create());
        sendCompany(
                event.company(),
                NotificationType.MEMBER_KICKED,
                playerArgs(event.player().getName()),
                member -> member.uuid().equals(kicked));
    }

    @EventHandler
    public void onOrderAccept(OrderAcceptEvent event) {
        var order = event.order();
        sendCompany(event.company(), NotificationType.ORDER_ACCEPTED, orderArgs(order));
        send(order.owner(), NotificationType.MY_ORDER_ACCEPTED, orderArgs(order));
    }

    /**
     * Tells the owner that an order is open again after the company working on it gave it up. The company is not
     * named.
     *
     * @param order order which is open again
     */
    public void notifyOrderOpen(ISimpleOrder order) {
        send(order.owner(), NotificationType.MY_ORDER_REOPENED, orderArgs(order));
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        sendCompany(event.company(), NotificationType.ORDER_ABORTED, orderArgs(event.order()));
        notifyOrderOpen(event.order());
    }

    @EventHandler
    public void onOrderRemoved(OrderRemovedEvent event) {
        send(event.order().owner(), NotificationType.MY_ORDER_REMOVED, orderArgs(event.order()));
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        send(event.order().owner(), NotificationType.MY_ORDER_DONE, orderArgs(event.order()));
        sendCompany(event.company(), NotificationType.ORDER_DELIVERED, orderArgs(event.order()));
    }

    @EventHandler
    public void onOrderExpiredEvent(OrderExpiredEvent event) {
        sendCompany(event.company(), NotificationType.ORDER_EXPIRED, orderArgs(event.order()));
        notifyOrderOpen(event.order());
    }

    /**
     * Tells a worker about a payout. A taxed payout shows the share before tax, the tax and the amount paid.
     *
     * @param event payment event
     */
    @EventHandler
    public void onOrderPayment(OrderPaymentEvent event) {
        var args = NotificationArgs.create()
                .money("amount", format(event.amount()))
                .text("order_name", event.order().fullName());
        if (event.tax() > 0) {
            args.money("gross", format(event.gross())).money("tax", format(event.tax()));
            send(event.player().getUniqueId(), NotificationType.PAYMENT_TAXED, args);
            return;
        }
        send(event.player().getUniqueId(), NotificationType.PAYMENT, args);
    }

    @EventHandler
    public void onCompanyLevelUp(CompanyLevelUpEvent event) {
        sendCompany(
                event.company(),
                NotificationType.LEVEL_UP,
                NotificationArgs.create().text("new_level", event.newLevel().levelName()));
    }

    @EventHandler
    public void onCompanyLevelDown(CompanyLevelDownEvent event) {
        sendCompany(
                event.company(),
                NotificationType.LEVEL_DOWN,
                NotificationArgs.create().text("new_level", event.newLevel().levelName()));
    }

    private String format(double amount) {
        return economy == null ? String.valueOf(amount) : economy.format(amount);
    }

    /**
     * The arguments naming an order: {@code order_name} and {@code order_id}.
     *
     * @param order the order
     * @return arguments
     */
    public static NotificationArgs orderArgs(ISimpleOrder order) {
        return NotificationArgs.create().text("order_name", order.fullName()).number("order_id", order.id());
    }

    private static NotificationArgs playerArgs(@Nullable String name) {
        return NotificationArgs.create().text("player_name", String.valueOf(name));
    }
}
