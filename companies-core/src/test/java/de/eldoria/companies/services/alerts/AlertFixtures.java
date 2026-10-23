/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Companies, orders and a notification recorder for the alert tests.
 */
final class AlertFixtures {
    static final int COMPANY = 7;
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private AlertFixtures() {}

    /**
     * A company of level 1 with an owner, a member who manages orders and one who does not.
     *
     * @param id id of the company
     * @return the company
     */
    static CompanyProfile company(int id) {
        return company(id, 1);
    }

    /**
     * A company with an owner, a member who manages orders and one who does not.
     *
     * @param id    id of the company
     * @param level level of the company
     * @return the company
     */
    static CompanyProfile company(int id, int level) {
        return new CompanyProfile(
                id,
                "Acme" + id,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                level,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(id, owner(id), CompanyPermission.OWNER.mask()),
                        CompanyMember.of(id, manager(id), CompanyPermission.MANAGE_ORDERS.mask()),
                        CompanyMember.of(id, worker(id), CompanyPermission.INVITE.mask())));
    }

    static UUID owner(int company) {
        return company == COMPANY ? OWNER : new UUID(company, 1);
    }

    static UUID manager(int company) {
        return company == COMPANY ? MANAGER : new UUID(company, 2);
    }

    static UUID worker(int company) {
        return company == COMPANY ? WORKER : new UUID(company, 3);
    }

    /**
     * An unclaimed order with one line.
     *
     * @param id       id of the order
     * @param name     name of the order
     * @param material material of its line
     * @param amount   items of its line
     * @param price    price of its line
     * @param owner    owner of the order
     * @return the order
     */
    static FullOrder order(int id, String name, Material material, int amount, double price, UUID owner) {
        var line = new OrderContent(0, new ItemStack(material), false, amount, price);
        return new FullOrder(
                id,
                owner,
                name,
                LocalDateTime.of(2026, 3, 1, 12, 0),
                0,
                LocalDateTime.of(2026, 3, 1, 12, 0),
                OrderState.UNCLAIMED,
                0.0,
                0,
                0,
                List.of(line));
    }

    static FullOrder order(int id, Material material, int amount, double price) {
        return order(id, "Supplies", material, amount, price, BUYER);
    }

    static String money(double amount) {
        return String.format(Locale.ROOT, "$%.2f", amount);
    }

    /**
     * One notification sent.
     *
     * @param recipient player notified
     * @param type      kind of notification
     * @param args      values of the notification
     */
    record Sent(UUID recipient, NotificationType type, NotificationArgs args) {
        String arg(String key) {
            var value = args.values().get(key);
            return value == null ? null : value.value();
        }
    }

    /**
     * Records the notifications sent.
     */
    static final class Recorder implements AlertNotices.Sender {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public synchronized void send(UUID recipient, NotificationType type, NotificationArgs args) {
            sent.add(new Sent(recipient, type, args));
        }

        List<UUID> recipients() {
            return sent.stream().map(Sent::recipient).toList();
        }
    }
}
