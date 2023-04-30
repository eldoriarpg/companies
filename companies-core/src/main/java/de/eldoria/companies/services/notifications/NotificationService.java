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
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Sends notifications based on internal events.
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

    public NotificationService(ANotificationData notificationData, AOrderData orderData,
                               ScheduledExecutorService workerPool, Plugin plugin) {
        this.notificationData = notificationData;
        this.orderData = orderData;
        this.workerPool = workerPool;
        sender = MessageSender.getPluginMessageSender(plugin);
        localizer = ILocalizer.getPluginLocalizer(plugin);
        this.plugin = plugin;
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
        var container = new NotificationData(sender.miniMessage()
                                                   .serialize(sender.serializeMessage(message, replacements)));
        notificationData.submitNotifications(player, container);
    }

    @EventHandler
    public void onCompanyLeaveEvent(CompanyLeaveEvent event) {
        sendCompanyMessage(event.company(), "notification.companyLeave",
                member -> member.uuid()
                                .equals(event.player()
                                             .getUniqueId()), Replacement.create("name", event.player().getName()));
    }

    public void sendCompanyMessage(ICompanyProfile company, String message, Predicate<ICompanyMember> filter, TagResolver... replacements) {
        for (var member : company.members()) {
            if (filter.test(member)) continue;
            if (!member.player()
                       .isOnline()) {
                saveNotification(member.player(), message, replacements);
                return;
            }
            sender.sendMessage(member.player()
                                     .getPlayer(), message, replacements);
        }
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        sendCompanyMessage(event.company(), "notification.companyJoin",
                member -> member.uuid()
                                .equals(event.player()
                                             .getUniqueId()), Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onCompanyKick(CompanyKickEvent event) {
        sendMessage(event.player(), "notification.kickedTarget");

        sendCompanyMessage(event.company(), "notification.kicked",
                member -> member.uuid()
                                .equals(event.player()
                                             .getUniqueId()), Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onOrderAccept(OrderAcceptEvent event) {
        var order = event.order();
        var message = MessageComposer.create()
                                     .text("<neutral>")
                                     .localeCode("notification.orderAccepted")
                                     .space()
                                     .text("<click:run_command:/company order info %s><add>[", order.id())
                                     .localeCode("words.info")
                                     .text("]</click>")
                                     .build();
        sendCompanyMessage(event.company(), message,
                miniOrderReplacement(order), Replacement.create("order_name", order.name()));
    }

    private TagResolver miniOrderReplacement(ISimpleOrder order) {
        return Replacement.create("order_name", String.format("<name>%s<neutral>", order.fullName()));
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        sendCompanyMessage(event.company(), "notification.orderCanceled",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderCanceled(OrderRemovedEvent event) {
        sendMessage(plugin.getServer()
                          .getOfflinePlayer(event.order()
                                                 .owner()), "notification.orderRemoved",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        var owner = plugin.getServer()
                          .getOfflinePlayer(event.order()
                                                 .owner());
        var message = MessageComposer.create()
                                     .text("<neutral>")
                                     .localeCode("notification.orderDone")
                                     .space()
                                     .text("<click:run_command:/order receive %s><add>[", event.order()
                                                                                               .id())
                                     .localeCode("notification.recieveItems")
                                     .text("]</click>")
                                     .build();
        sendMessage(owner, message,
                miniOrderReplacement(event.order()), Replacement.create("order_name", event.order()
                                                                                           .name()));

        sendCompanyMessage(event.company(), "notification.orderDelivered",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderExpiredEvent(OrderExpiredEvent event) {
        sendCompanyMessage(event.company(), "notification.companyOrderExpired",
                Replacement.create("order_name", event.order()
                                                      .fullName()));
    }

    @EventHandler
    public void onOrderPayment(OrderPaymentEvent event) {
        sendMessage(event.player(), "notification.orderPayment",
                Replacement.create("amount", event.amount()),
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onCompanyLevelUp(CompanyLevelUpEvent event) {
        sendCompanyMessage(event.company(), "notification.companyLevelUp",
                Replacement.create("new_level", event.newLevel().levelName()));
    }

    @EventHandler
    public void onCompanyLevelDown(CompanyLevelDownEvent event) {
        sendCompanyMessage(event.company(), "notification.companyLevelDown",
                Replacement.create("new_level", event.newLevel().levelName()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        workerPool.schedule(() -> {
            sendNotifications(event.getPlayer());
            sendDeliveredOrders(event.getPlayer());
        }, 1L, TimeUnit.SECONDS);
    }

    private void sendDeliveredOrders(Player player) {
        var orders = orderData.retrieveOrdersByPlayer(player, OrderState.DELIVERED, OrderState.DELIVERED).join();
        if (orders == null) return;
        for (var order : orders) {
            var message = MessageComposer.create()
                                         .text("<neutral>")
                                         .localeCode("notification.orderDone")
                                         .space()
                                         .text("<click:run_command:/order receive %s><add>[", order.id())
                                         .localeCode("notification.recieveItems")
                                         .text("]</click>")
                                         .build();
            sendMessage(player, message, miniOrderReplacement(order),
                    Replacement.create("order_name", order.name()));
        }
    }

    private void sendNotifications(Player player) {
        var notifications = notificationData.retrieveNotifications(player).join();
        List<String> components = new ArrayList<>();

        for (var currDate : notifications) {
            var date = currDate.getKey();
            if (date.equals(LocalDate.now())) {
                // Today
                components.add("<heading><l18n:words.today>:<neutral>");
            } else if (date.equals(LocalDate.now().minusDays(1L))) {
                // Yesterday
                components.add("<heading><l18n:words.yesterday>:<neutral>");
            } else {
                components.add(String.format("<heading>%s:<neutral>", DATE_FORMATTER.format(date)));
            }
            for (var notification : currDate.getValue()) {
                var time = TIME_FORMATTER.format(notification.created());
                var data = notification.data();
                components.add(String.format("<name>%s:<neutral> %s", time, localizer.localize(data.message())));
            }
        }
        notificationData.submitNotificationClear(player);
        if (!notifications.isEmpty()) {
            sender.sendMessage(player, String.join("\n", components));
        }
    }
}
