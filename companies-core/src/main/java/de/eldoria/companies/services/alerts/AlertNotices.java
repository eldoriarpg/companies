/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.Payout;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;

import java.util.List;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

/**
 * The notifications of alerts, sent to the members of a company who manage its orders.
 * <p>
 * Orders are anonymous: a notification names the order, its contents and its price, never its owner. Notifications go
 * through the inbox, so members on other servers and offline members get them as well.
 */
public final class AlertNotices {
    /**
     * Lines of an order listed in a notification before the rest is left out.
     */
    static final int SHOWN_LINES = 5;

    private final Sender sender;
    private final DoubleFunction<String> money;

    /**
     * @param sender sends one notification
     * @param money  formats amounts of money
     */
    public AlertNotices(Sender sender, DoubleFunction<String> money) {
        this.sender = sender;
        this.money = money;
    }

    /**
     * Tells a company that a new order matches some of its alerts.
     *
     * @param company    the company
     * @param alertNames names of the alerts that matched
     * @param order      the new order
     * @param taxPercent tax the company pays on payouts
     */
    public void orderMatched(CompanyProfile company, List<String> alertNames, FullOrder order, double taxPercent) {
        sendManagers(company, NotificationType.ORDER_ALERT, orderArgs(alertNames, order, taxPercent));
    }

    /**
     * Tells a company how many orders matched an alert while it was rate limited.
     *
     * @param company the company
     * @param alert   the alert
     * @param count   orders held back
     */
    public void digest(CompanyProfile company, OrderAlert alert, int count) {
        sendManagers(
                company,
                NotificationType.ORDER_ALERT_DIGEST,
                NotificationArgs.create().text("alert_name", alert.name()).number("count", count));
    }

    /**
     * The arguments of {@link NotificationType#ORDER_ALERT}.
     *
     * @param alertNames names of the alerts that matched
     * @param order      the new order
     * @param taxPercent tax the company pays on payouts
     * @return arguments without the owner of the order
     */
    NotificationArgs orderArgs(List<String> alertNames, FullOrder order, double taxPercent) {
        return NotificationArgs.create()
                .text("alert_name", String.join(", ", alertNames))
                .text("order_name", order.fullName())
                .number("order_id", order.id())
                .text("contents", contents(order))
                .money("amount", money.apply(order.price()))
                .money(
                        "net",
                        money.apply(Payout.split(order.price(), taxPercent).net()));
    }

    /**
     * The lines of an order as {@code 64 iron_ingot, 32 gold_ingot}, the first {@link #SHOWN_LINES} of them.
     *
     * @param order the order
     * @return contents of the order
     */
    static String contents(FullOrder order) {
        var lines = order.contents().stream()
                .limit(SHOWN_LINES)
                .map(line -> line.amount() + " " + line.materialIdentifier())
                .collect(Collectors.joining(", "));
        return order.contents().size() > SHOWN_LINES ? lines + ", …" : lines;
    }

    private void sendManagers(CompanyProfile company, NotificationType type, NotificationArgs args) {
        for (var member : company.members(CompanyPermission.MANAGE_ORDERS)) {
            sender.send(member.uuid(), type, args);
        }
    }

    /**
     * Sends a notification to one player.
     */
    @FunctionalInterface
    public interface Sender {
        /**
         * @param recipient player to notify
         * @param type      kind of notification
         * @param args      values of the notification
         */
        void send(UUID recipient, NotificationType type, NotificationArgs args);
    }
}
