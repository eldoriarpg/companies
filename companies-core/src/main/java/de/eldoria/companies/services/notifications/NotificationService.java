package de.eldoria.companies.services.notifications;

import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.data.repository.ANotificationData;
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
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
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
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.companyDisband");
    }

    @EventHandler
    public void onCompanyLeaveEvent(CompanyLeaveEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.companyLeave",
                member -> member.uuid().equals(event.player().getUniqueId()), Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.companyJoin",
                member -> member.uuid().equals(event.player().getUniqueId()), Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onCompanyKick(CompanyKickEvent event) {
        sendMessage(event.player(), "notification.kickedTarget");

        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.kicked",
                member -> member.uuid().equals(event.player().getUniqueId()), Replacement.create("name", event.player().getName()));
    }

    @EventHandler
    public void onOrderAccept(OrderAcceptEvent event) {
        var order = event.order();
        var message = MessageComposer.create()
                .text("<%s>", Colors.NEUTRAL)
                .localeCode("notification.orderAccepted")
                .space()
                .text("<click:run_command:/company order info %s><%s>[", order.id(), Colors.ADD)
                .localeCode("words.info")
                .text("]</click>")
                .build();
        sendCompanyMessage(MessageType.MINI_MESSAGE, event.company(), message,
                miniOrderReplacement(order), Replacement.create("order_name", order.name()));
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.orderCanceled",
                Replacement.create("order_name", event.order().fullName()).addFormatting('6'));
    }

    @EventHandler
    public void onOrderCanceled(OrderRemovedEvent event) {
        sendMessage(plugin.getServer().getOfflinePlayer(event.order().owner()), "notification.orderRemoved",
                Replacement.create("order_name", event.order().fullName()).addFormatting('6'));
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        var owner = plugin.getServer().getOfflinePlayer(event.order().owner());
        var message = MessageComposer.create()
                .text("<%s>", Colors.NEUTRAL)
                .localeCode("notification.orderDone")
                .space()
                .text("<click:run_command:/order receive %s><%s>[", event.order().id(), Colors.ADD)
                .localeCode("notification.recieveItems")
                .text("]</click>")
                .build();
        sendMiniMessage(owner, message,
                miniOrderReplacement(event.order()), Replacement.create("order_name", event.order().name()));

        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.orderDelivered",
                Replacement.create("order_name", event.order().fullName()).addFormatting('6'));
    }

    @EventHandler
    public void onOrderExpiredEvent(OrderExpiredEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.companyOrderExpired",
                Replacement.create("order_name", event.order().fullName()));
    }

    @EventHandler
    public void onOrderPayment(OrderPaymentEvent event) {
        sendMessage(event.player(), "notification.orderPayment",
                Replacement.create("amount", event.amount()).addFormatting('6'),
                Replacement.create("order_name", event.order().fullName()).addFormatting('6'));
    }

    @EventHandler
    public void onCompanyLevelUp(CompanyLevelUpEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.companyLevelUp",
                Replacement.create("NEW_LEVEL", event.newLevel().levelName()).addFormatting('6'));
    }

    @EventHandler
    public void onCompanyLevelDown(CompanyLevelDownEvent event) {
        sendCompanyMessage(MessageType.SIMPLE_MESSAGE, event.company(), "notification.companyLevelDown",
                Replacement.create("NEW_LEVEL", event.newLevel().levelName()).addFormatting('6'));
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
                            components.add(miniMessage.parse(localizer.localize(String.format("<%s>$%s$:<%s>", Colors.HEADING, "words.today", Colors.NEUTRAL))));
                        } else if (date.equals(LocalDate.now().minusDays(1))) {
                            // Yesterday
                            components.add(miniMessage.parse(localizer.localize(String.format("<%s>$%s$:<%s>", Colors.HEADING, "words.yesterday", Colors.NEUTRAL))));
                        } else {
                            components.add(miniMessage.parse(String.format("<%s>$%s$:<%s>", Colors.HEADING, DATE_FORMATTER.format(date), Colors.NEUTRAL)));
                        }
                        for (var notification : currDate.getValue()) {
                            var time = TIME_FORMATTER.format(notification.created());
                            var data = notification.data();
                            switch (data.type()) {
                                case MINI_MESSAGE:
                                    components.add(miniMessage.parse(String.format("<%s>%s:<%s> %s", Colors.NAME, time, Colors.NEUTRAL, localizer.localize(data.message(), data.replacements()))));
                                    break;
                                case SIMPLE_MESSAGE:
                                    components.add(Component.text(de.eldoria.eldoutilities.messages.MessageType.NORMAL.forceColor("§b" + time + " §2" + localizer.localize(data.message(), data.replacements()))));
                                    break;
                            }
                        }
                    }
                    notificationData.submitNotificationClear(event.getPlayer());
                    audiences.player(event.getPlayer()).sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), components));
                }).exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "", err);
                    return null;
                });
    }

    public void sendCompanyMessage(MessageType type, ICompanyProfile company, String message, Replacement... replacements) {
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

    public void sendCompanyMessage(MessageType type, ICompanyProfile company, String message, Predicate<ICompanyMember> filter, Replacement... replacements) {
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

    private Replacement miniOrderReplacement(ISimpleOrder order) {
        return Replacement.create("order_name", String.format("<%s>%s<%s>", Colors.NAME, order.fullName(), Colors.NEUTRAL));
    }
}
