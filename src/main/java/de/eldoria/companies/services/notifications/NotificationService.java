package de.eldoria.companies.services.notifications;

import de.eldoria.companies.data.repository.ANotificationData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Sends notifications based on internal events.
 */
public class NotificationService implements Listener {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final ANotificationData notificationData;
    private final MessageSender sender;
    private final ILocalizer localizer;
    private final MiniMessage miniMessage;
    private final BukkitAudiences audiences;
    private final Plugin plugin;

    public NotificationService(ANotificationData notificationData, Plugin plugin) {
        this.notificationData = notificationData;
        sender = MessageSender.getPluginMessageSender(plugin);
        localizer = ILocalizer.getPluginLocalizer(plugin);
        this.plugin = plugin;
        miniMessage = MiniMessage.builder().build();
        audiences = BukkitAudiences.create(plugin);
    }

    @EventHandler
    public void onCompanyDisband(CompanyDisbandEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "Your company was disbanded.");
    }

    @EventHandler
    public void onCompanyLeaveEvent(CompanyLeaveEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), event.player().getName() + " has left the company.",
                member -> member.uuid().equals(event.player().getUniqueId()));
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), event.player().getName() + " has joined the company.",
                member -> member.uuid().equals(event.player().getUniqueId()));
    }

    @EventHandler
    public void onCompanyKick(CompanyKickEvent event) {
        sendMessage(event.player(), "You were kicked from your company.");

        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), event.player().getName() + " was kicked from the company",
                member -> member.uuid().equals(event.player().getUniqueId()));
    }

    @EventHandler
    public void onOrderAccept(OrderAcceptEvent event) {
        var order = event.order();
        var message = "The company accepted the order <b>%order_name%</b> <click:run_command:/company order info %order_id%>[Info]</click>";
        sendCompanyMessage(MessageType.MINI_MESSAGE, event.company(), message,
                Replacement.create("order_name", order.fullName()), Replacement.create("order_id", order.id()));
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "The order %order_name% was canceled by your company.",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        var owner = plugin.getServer().getOfflinePlayer(event.order().owner());
        var message = "Your order %order_name% was delivered. <click:run_command:/order receive %order_id%>[Receive your items]</click>";
        sendMiniMessage(owner, message,
                Replacement.create("order_name", event.order().fullName()), Replacement.create("order_id", event.order().id()));

        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "Order %order_name% delivered.",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderExpiredEvent(OrderExpiredEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "The order %order_name% expired before deliverd. Already delivered items are lost.",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderPayment(OrderPaymentEvent event) {
        sendMessage(event.player(), "You received " + event.amount() + " for your contribution to order " + event.order().fullName());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        notificationData.retrieveNotifications(event.getPlayer())
                .thenAccept(notifications -> {
                    List<Component> components = new ArrayList<>();

                    for (var currDate : notifications) {
                        var date = currDate.getKey();
                        if (date.equals(LocalDate.now())) {
                            // Today
                            components.add(miniMessage.parse("Today:"));
                        } else if (date.equals(LocalDate.now().minusDays(1))) {
                            // Yesterday
                            components.add(miniMessage.parse("Yesterday:"));
                        } else {
                            components.add(miniMessage.parse(DATE_FORMATTER.format(date)));
                        }
                        for (var notification : currDate.getValue()) {
                            var time = TIME_FORMATTER.format(notification.created());
                            var data = notification.data();
                            switch (data.type()) {
                                case MINI_MESSAGE:
                                    components.add(miniMessage.parse(time + " " + localizer.localize(data.message(), data.replacements())));
                                    break;
                                case SIMPLE_MESSAGE:
                                    components.add(Component.text(time + " " + localizer.localize(data.message(), data.replacements())));
                                    break;
                            }
                        }
                    }
                    notificationData.submitNotificationClear(event.getPlayer());
                    audiences.player(event.getPlayer()).sendMessage(Component.join(Component.newline(), components));
                }).exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "", err);
                    return null;
                });
    }

    public void sendCompanyMessage(MessageType type, CompanyProfile company, String message, Replacement... replacements) {
        for (var member : company.members()) {
            switch (type) {
                case MINI_MESSAGE:
                    sendMiniMessage(member.player(), message, replacements);
                    break;
                case SIMPLE_MESSAGE:
                    sendMessage(member.player(), message, replacements);
                    break;
            }
        }
    }

    public void sendCompanyMessage(MessageType type, CompanyProfile company, String message, Predicate<CompanyMember> filter, Replacement... replacements) {
        for (var member : company.members()) {
            if (filter.test(member)) continue;
            switch (type) {
                case MINI_MESSAGE:
                    sendMiniMessage(member.player(), message, replacements);
                    break;
                case SIMPLE_MESSAGE:
                    sendMessage(member.player(), message, replacements);
                    break;
            }
        }
    }

    public void sendMessage(OfflinePlayer player, String message, Replacement... replacements) {
        if (!player.isOnline()) {
            saveNotification(player, MessageType.SIMPLE_MESSAGE, message, replacements);
            return;
        }

        sender.sendMessage(player.getPlayer(), localizer.getMessage(message, replacements));
    }

    public void sendMiniMessage(OfflinePlayer player, String message, Replacement... replacements) {
        if (!player.isOnline()) {
            saveNotification(player, MessageType.MINI_MESSAGE, message, replacements);
            return;
        }

        audiences.player(player.getPlayer()).sendMessage(miniMessage.parse(localizer.localize(message, replacements)));
    }

    private void saveNotification(OfflinePlayer player, MessageType type, String message, Replacement... replacements) {
        var container = new NotificationData(type, message, replacements);
        notificationData.submitNotifications(player, container);
    }
}
