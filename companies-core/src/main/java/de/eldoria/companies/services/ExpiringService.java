/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.SoloOrderService;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
 * members on join about claimed orders that are about to expire. Orders players work on alone are released and warned
 * about the same way, with the solo delivery time.
 */
public class ExpiringService implements Runnable, Listener {
    private static final String COMPANY_ORDER_INFO = "/company order info ";
    private static final String SOLO_ORDER_INFO = "/order work info ";

    private final Plugin plugin;
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final MessageSender messageSender;
    private final Economy economy;
    private final NotificationService notificationService;
    private final OrderOutcomes outcomes;
    private final SoloOrderService soloOrders;

    /**
     * @param plugin              the plugin
     * @param orderData           order repository
     * @param companyData         company repository
     * @param configuration       configuration of the plugin
     * @param economy             refunds purged orders
     * @param notificationService warns owners
     * @param outcomes            releases and purges orders and records their history
     * @param soloOrders          releases the orders players work on alone
     */
    public ExpiringService(
            Plugin plugin,
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Economy economy,
            NotificationService notificationService,
            OrderOutcomes outcomes,
            SoloOrderService soloOrders) {
        messageSender = MessageSender.getPluginMessageSender(plugin);
        this.plugin = plugin;
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
        this.economy = economy;
        this.notificationService = notificationService;
        this.outcomes = outcomes;
        this.soloOrders = soloOrders;
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
                        .whenComplete(
                                orders -> reportOrderExpiring(player, expiringOrders(orders), COMPANY_ORDER_INFO))));
        orderData
                .retrieveOrdersBySoloWorker(player.getUniqueId(), OrderState.CLAIMED, OrderState.CLAIMED)
                .whenComplete(orders -> reportOrderExpiring(player, expiringOrders(orders), SOLO_ORDER_INFO));
    }

    private List<SimpleOrder> expiringOrders(List<SimpleOrder> orders) {
        var settings = configuration.companySettings();
        var now = Times.now();
        return orders.stream()
                .filter(order -> expiresSoon(
                        order.claimed(), now, order.deliveryHours(configuration), settings.expiryWarningHours()))
                .toList();
    }

    private void reportOrderExpiring(Player player, List<SimpleOrder> orders, String infoCommand) {
        if (orders.isEmpty() || !player.isOnline()) return;
        var text = MenuText.create().locale("expiring.notice");
        for (var index = 0; index < orders.size(); index++) {
            text.newLine().append(expiringElement(player, orders.get(index), index, infoCommand));
        }
        messageSender.sendMessage(player, text);
    }

    /**
     * One expiring order with a button opening it. The element's own text uses {@code order_name} and
     * {@code duration}, so it is rendered on its own and inserted as a component.
     *
     * @param player      player receiving the notice
     * @param order       expiring order
     * @param index       position of the order in the notice
     * @param infoCommand command showing the order, followed by its id
     * @return the line of the order
     */
    private MenuText expiringElement(Player player, SimpleOrder order, int index, String infoCommand) {
        var element = Keys.of("order", index);
        var info = Keys.of("order", index, "info");
        var line = MenuText.create()
                .locale(
                        "expiring.element",
                        Values.text("order_name", order.fullName()),
                        Values.text("duration", order.runningOutTime(configuration)));
        return MenuText.create()
                .text(
                        "<" + element + "> <" + info + ">[",
                        Values.component(
                                element, messageSender.serializeMessage(player, line.build(), resolvers(line))),
                        Values.click(info, ClickEvent.runCommand(infoCommand + order.id())))
                .locale("words.info")
                .text("]</" + info + ">");
    }

    @Override
    public void run() {
        try {
            releaseExpiredOrders();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not release expired orders", e);
        }
        try {
            soloOrders.releaseExpired().join();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not release expired solo orders", e);
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
            var left = Duration.between(Times.now(), order.claimed().plusHours(settings.maxUnclaimedHours()));
            var args = NotificationService.orderArgs(order)
                    .text("duration", Texts.prettyDuration(left.isNegative() ? Duration.ZERO : left));
            notificationService.send(order.owner(), NotificationType.MY_ORDER_PURGE_WARNING, args);
        }
    }

    private static TagResolver[] resolvers(MenuText text) {
        return text.replacements().toArray(TagResolver[]::new);
    }

    private void releaseExpiredOrders() {
        var expired = orderData
                .retrieveExpiredOrders(configuration.companySettings().deliveryHours())
                .join();

        for (var order : expired) {
            var returned = outcomes.expire(order).join();
            if (returned == null || returned.isEmpty()) continue;
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
     * Removes orders nobody accepted in time and refunds their price and fee to the owner. A system order is removed
     * without a refund.
     * <p>
     * TODO refund a removed system order to the money source of its rule.
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
            if (!Boolean.TRUE.equals(outcomes.purge(fullOrder).join())) {
                continue;
            }
            if (SystemOwner.is(order.owner())) continue;
            var owner = plugin.getServer().getOfflinePlayer(order.owner());
            Threading.mainThread(plugin)
                    .execute(() -> economy.depositPlayer(owner, fullOrder.price() + fullOrder.fee()));
        }
    }
}
