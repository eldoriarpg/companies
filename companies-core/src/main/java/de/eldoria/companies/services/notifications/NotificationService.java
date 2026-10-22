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
import de.eldoria.companies.data.repository.ANotificationData;
import de.eldoria.companies.data.repository.AOrderData;
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
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Sends notifications based on internal events.
 * <p>
 * Money is formatted by the Vault economy, which is registered before this service is created.
 */
public class NotificationService implements Listener {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final ANotificationData notificationData;
    private final AOrderData orderData;
    private final ScheduledExecutorService workerPool;
    private final MessageSender sender;
    private final ILocalizer localizer;
    private final Plugin plugin;
    private final Economy economy;

    public NotificationService(
            ANotificationData notificationData,
            AOrderData orderData,
            ScheduledExecutorService workerPool,
            Plugin plugin) {
        this.notificationData = notificationData;
        this.orderData = orderData;
        this.workerPool = workerPool;
        sender = MessageSender.getPluginMessageSender(plugin);
        localizer = ILocalizer.getPluginLocalizer(plugin);
        this.plugin = plugin;
        economy = plugin.getServer().getServicesManager().load(Economy.class);
    }

    @EventHandler
    public void onCompanyDisband(CompanyDisbandEvent event) {
        sendCompanyMessage(event.company(), "notification.companyDisband");
    }

    public void sendCompanyMessage(ICompanyProfile company, String message, TagResolver... replacements) {
        for (var member : company.members()) {
            sendMessage(member.player(), message, replacements);
        }
    }

    public void sendMessage(OfflinePlayer player, String message, TagResolver... replacements) {
        if (!player.isOnline()) {
            saveNotification(player, message, replacements);
            return;
        }
        sender.sendMessage(player.getPlayer(), message, replacements);
    }

    private void saveNotification(OfflinePlayer player, String message, TagResolver... replacements) {
        var container =
                new NotificationData(sender.miniMessage().serialize(sender.serializeMessage(message, replacements)));
        notificationData.submitNotifications(player, container);
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
        sendCompanyMessage(
                event.company(),
                "notification.companyLeave",
                member -> member.uuid().equals(event.player().getUniqueId()),
                Replacement.create("name", event.player().getName()));
    }

    public void sendCompanyMessage(
            ICompanyProfile company, String message, Predicate<ICompanyMember> filter, TagResolver... replacements) {
        for (var member : company.members()) {
            if (filter.test(member)) continue;
            if (!member.player().isOnline()) {
                saveNotification(member.player(), message, replacements);
                continue;
            }
            sender.sendMessage(member.player().getPlayer(), message, replacements);
        }
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        sendCompanyMessage(
                event.company(),
                "notification.companyJoin",
                member -> member.uuid().equals(event.player().getUniqueId()),
                Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onCompanyKick(CompanyKickEvent event) {
        sendMessage(event.player(), "notification.kickedTarget");

        sendCompanyMessage(
                event.company(),
                "notification.kicked",
                member -> member.uuid().equals(event.player().getUniqueId()),
                Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onOrderAccept(OrderAcceptEvent event) {
        var order = event.order();
        var message =
                orderNotice("notification.orderAccepted", order, "/company order info " + order.id(), "words.info");
        sendCompanyMessage(event.company(), message.build(), resolvers(message));
        sendMessage(
                plugin.getServer().getOfflinePlayer(order.owner()),
                orderNotice("notification.ownerOrderAccepted", order, "/order info " + order.id(), "words.info"));
    }

    /**
     * Tells the owner that an order is open again after the company working on it gave it up. The company is not
     * named.
     *
     * @param order order which is open again
     */
    public void notifyOrderOpen(ISimpleOrder order) {
        sendMessage(
                plugin.getServer().getOfflinePlayer(order.owner()),
                orderNotice("notification.ownerOrderOpen", order, "/order info " + order.id(), "words.info"));
    }

    /**
     * Sends a message, or stores it when the player is offline. Click actions of the message survive the storing.
     *
     * @param player  receiving player
     * @param message the message with its placeholders
     */
    public void sendMessage(OfflinePlayer player, MenuText message) {
        sendMessage(player, message.build(), resolvers(message));
    }

    /**
     * A notice about an order with a button running a command on it, using the placeholders {@code order_name} and
     * {@code order_action}.
     *
     * @param key     locale text of the notice
     * @param order   order the notice is about
     * @param command command the button runs
     * @param button  locale text of the button
     * @return the notice
     */
    private static MenuText orderNotice(String key, ISimpleOrder order, String command, String button) {
        return MenuText.create()
                .text("<neutral>")
                .locale(key, Values.text("order_name", order.fullName()))
                .space()
                .text("<order_action><add>[", Values.click("order_action", ClickEvent.runCommand(command)))
                .locale(button)
                .text("]</order_action>");
    }

    private static TagResolver[] resolvers(MenuText message) {
        return message.replacements().toArray(TagResolver[]::new);
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        sendCompanyMessage(
                event.company(),
                "notification.orderCanceled",
                Replacement.unparsed("order_name", event.order().fullName()));
        notifyOrderOpen(event.order());
    }

    @EventHandler
    public void onOrderCanceled(OrderRemovedEvent event) {
        sendMessage(
                plugin.getServer().getOfflinePlayer(event.order().owner()),
                "notification.orderRemoved",
                Replacement.unparsed("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        var owner = plugin.getServer().getOfflinePlayer(event.order().owner());
        sendMessage(owner, orderDoneNotice(event.order()));

        sendCompanyMessage(
                event.company(),
                "notification.orderDelivered",
                Replacement.unparsed("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderExpiredEvent(OrderExpiredEvent event) {
        sendCompanyMessage(
                event.company(),
                "notification.companyOrderExpired",
                Replacement.unparsed("order_name", event.order().fullName()));
        notifyOrderOpen(event.order());
    }

    /**
     * Tells a worker about a payout. A taxed payout shows the share before tax, the tax and the amount paid.
     *
     * @param event payment event
     */
    @EventHandler
    public void onOrderPayment(OrderPaymentEvent event) {
        var amount = Replacement.create("amount", economy.format(event.amount()));
        var orderName = Replacement.unparsed("order_name", event.order().fullName());
        if (event.tax() > 0) {
            sendMessage(
                    event.player(),
                    "notification.orderPaymentTaxed",
                    amount,
                    orderName,
                    Replacement.create("gross", economy.format(event.gross())),
                    Replacement.create("tax", economy.format(event.tax())));
            return;
        }
        sendMessage(event.player(), "notification.orderPayment", amount, orderName);
    }

    @EventHandler
    public void onCompanyLevelUp(CompanyLevelUpEvent event) {
        sendCompanyMessage(
                event.company(),
                "notification.companyLevelUp",
                Replacement.create("new_level", event.newLevel().levelName()));
    }

    @EventHandler
    public void onCompanyLevelDown(CompanyLevelDownEvent event) {
        sendCompanyMessage(
                event.company(),
                "notification.companyLevelDown",
                Replacement.create("new_level", event.newLevel().levelName()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        workerPool.schedule(
                () -> {
                    sendNotifications(event.getPlayer());
                    sendDeliveredOrders(event.getPlayer());
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
            sendMessage(player, orderDoneNotice(order));
        }
    }

    private static MenuText orderDoneNotice(ISimpleOrder order) {
        return orderNotice(
                "notification.orderDone", order, "/order receive " + order.id(), "notification.receiveItems");
    }

    private void sendNotifications(Player player) {
        var notifications = notificationData.retrieveNotifications(player).join();
        notificationData.submitNotificationClear(player);
        if (notifications.isEmpty()) return;
        var text = MenuText.create();
        var day = 0;
        for (var currDate : notifications) {
            if (day > 0) text.newLine();
            text.text("<heading>").append(dayLabel(currDate.getKey(), day)).text(":<neutral>");
            var entry = 0;
            for (var notification : currDate.getValue()) {
                var time = Values.text(Keys.of("time", day, entry++), TIME_FORMATTER.format(notification.created()));
                text.newLine()
                        .text("<name><" + time.key() + ">:<neutral> ", time)
                        .text(localizer.localize(notification.data().message()));
            }
            day++;
        }
        sender.sendMessage(player, text);
    }

    private static MenuText dayLabel(LocalDate date, int day) {
        if (date.equals(LocalDate.now())) return MenuText.create().locale("words.today");
        if (date.equals(LocalDate.now().minusDays(1L))) return MenuText.create().locale("words.yesterday");
        var key = Keys.of("date", day);
        return MenuText.create().text("<" + key + ">", Values.text(key, DATE_FORMATTER.format(date)));
    }
}
