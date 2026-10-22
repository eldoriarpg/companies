/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Releases claimed orders after their deadline, warns owners before and purges orders nobody claimed, and warns company
 * members on join about claimed orders that are about to expire.
 */
public class ExpiringService implements Runnable, Listener {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final MessageSender messageSender;
    private final ItemReturnService itemReturnService;
    private final Economy economy;
    private final NotificationService notificationService;

    public ExpiringService(
            Plugin plugin,
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            ItemReturnService itemReturnService,
            Economy economy,
            NotificationService notificationService) {
        messageSender = MessageSender.getPluginMessageSender(plugin);
        this.plugin = plugin;
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
        this.itemReturnService = itemReturnService;
        this.economy = economy;
        this.notificationService = notificationService;
    }

    /**
     * Decides whether a claimed order is inside the warning window before its deadline.
     *
     * @param claimed       when the order was claimed
     * @param now           the current time
     * @param deliveryHours hours a company has to deliver a claimed order
     * @param warningHours  hours before the deadline in which members are warned
     * @return true when the deadline is less than {@code warningHours} away and not yet reached
     */
    public static boolean expiresSoon(LocalDateTime claimed, LocalDateTime now, int deliveryHours, int warningHours) {
        if (claimed == null) return false;
        var deadline = claimed.plusHours(deliveryHours);
        return now.isBefore(deadline) && !now.isBefore(deadline.minusHours(warningHours));
    }

    /**
     * Starts releasing expired and purging dead orders. Only the primary node does this.
     *
     * @param executorService executor that runs the periodic check
     */
    public void start(ScheduledExecutorService executorService) {
        var interval = configuration.generalSettings().orderCheckInterval();
        executorService.scheduleAtFixedRate(this, 10L, interval, TimeUnit.MINUTES);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        companyData
                .retrievePlayerCompany(player)
                .whenComplete(optCompany -> optCompany.ifPresent(company -> orderData
                        .retrieveOrdersByCompany(company, OrderState.CLAIMED, OrderState.CLAIMED)
                        .whenComplete(orders -> reportOrderExpiring(player, expiringOrders(orders)))));
    }

    private List<SimpleOrder> expiringOrders(List<SimpleOrder> orders) {
        var settings = configuration.companySettings();
        var now = LocalDateTime.now();
        return orders.stream()
                .filter(order -> expiresSoon(
                        order.claimed(), now, order.deliveryHours(configuration), settings.expiryWarningHours()))
                .toList();
    }

    private void reportOrderExpiring(Player player, List<SimpleOrder> orders) {
        if (orders.isEmpty() || !player.isOnline()) return;
        var composer = MessageComposer.create().localeCode("expiring.notice").newLine();
        for (var order : orders) {
            composer.localeCode(
                            "expiring.element",
                            Replacement.unparsed("name", order.fullName()),
                            Replacement.create("duration", order.runningOutTime(configuration)))
                    .text("<click:run_command:/company order info %s>[", order.id())
                    .localeCode("words.info")
                    .text("]</click>")
                    .newLine();
        }
        messageSender.sendMessage(player, composer);
    }

    @Override
    public void run() {
        try {
            releaseExpiredOrders();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not release expired orders", e);
        }
        try {
            warnOwners();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not warn owners about purged orders", e);
        }
        try {
            purgeDeadOrders();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not purge dead orders", e);
        }
    }

    /**
     * Warns owners of unaccepted orders that are purged within {@code purgeWarningHours}, once per purge timer.
     */
    private void warnOwners() {
        var settings = configuration.orderSetting();
        var orders = orderData
                .retrievePurgeWarnings(settings.maxUnclaimedHours(), settings.purgeWarningHours())
                .join();
        for (var order : orders) {
            if (!Boolean.TRUE.equals(orderData.submitPurgeWarning(order).join())) continue;
            var left = Duration.between(LocalDateTime.now(), order.claimed().plusHours(settings.maxUnclaimedHours()));
            notificationService.sendMessage(
                    plugin.getServer().getOfflinePlayer(order.owner()),
                    purgeWarning(order, settings.maxExtensions()),
                    Replacement.unparsed("order_name", order.fullName()),
                    Replacement.create("duration", Texts.prettyDuration(left.isNegative() ? Duration.ZERO : left)),
                    Replacement.create("left", order.extensionsLeft(settings.maxExtensions())));
        }
    }

    private String purgeWarning(SimpleOrder order, int maxExtensions) {
        var composer = MessageComposer.create().text("<neutral>").localeCode("notification.orderPurgeWarning");
        if (order.extensionsLeft(maxExtensions) > 0) {
            composer.space()
                    .text("<click:run_command:/order extend %s><modify>[", order.id())
                    .localeCode("order.info.extend")
                    .text("]</click>");
        }
        return composer.space()
                .text("<click:suggest_command:/order raise %s ><modify>[", order.id())
                .localeCode("order.info.raise")
                .text("]</click>")
                .build();
    }

    private void releaseExpiredOrders() {
        var expired = orderData
                .retrieveExpiredOrders(configuration.companySettings().deliveryHours())
                .join();

        for (var order : expired) {
            var workers = orderData.submitUnclaimOrder(order).join();
            if (workers == null || workers.isEmpty()) continue;
            itemReturnService.notifyWorkers(workers.get());
            var profile = companyData
                    .retrieveCompanyById(order.company())
                    .asFuture()
                    .exceptionally(err -> {
                        plugin.getLogger().log(Level.SEVERE, "Something went wrong", err);
                        return Optional.empty();
                    })
                    .thenApply(r ->
                            r.flatMap(p -> companyData.retrieveCompanyProfile(p).join()))
                    .join();
            if (profile.isEmpty()) continue;
            plugin.getServer().getPluginManager().callEvent(new OrderExpiredEvent(order, profile.get()));
        }
    }

    /**
     * Removes orders nobody accepted in time and refunds their price and fee to the owner.
     */
    private void purgeDeadOrders() {
        var expired = orderData
                .retrieveDeadOrders(configuration.orderSetting().maxUnclaimedHours())
                .join();

        for (var order : expired) {
            var fullOrder = orderData.retrieveFullOrder(order).join();
            if (fullOrder == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(orderData.submitOrderPurge(order).join())) {
                continue;
            }
            var owner = plugin.getServer().getOfflinePlayer(order.owner());
            Threading.mainThread(plugin)
                    .execute(() -> economy.depositPlayer(owner, fullOrder.price() + fullOrder.fee()));
            plugin.getServer().getPluginManager().callEvent(new OrderRemovedEvent(order));
        }
    }
}
