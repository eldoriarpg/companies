/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class NotificationArgsTest {
    @Test
    void argumentsKeepTheirOrderAndKind() {
        var args = NotificationArgs.create()
                .text("company_name", "Miners")
                .money("amount", "$5.00")
                .number("count", 3);

        Assertions.assertEquals(
                List.of("company_name", "amount", "count"),
                List.copyOf(args.values().keySet()));
        Assertions.assertEquals(
                NotificationArgs.Kind.MONEY, args.values().get("amount").kind());
        Assertions.assertEquals("3", args.values().get("count").value());
        Assertions.assertEquals(3, args.resolvers().length);
    }

    @Test
    void actionPlaceholdersAreFilled() {
        var args = NotificationArgs.create().number("order_id", 42);

        Assertions.assertEquals("/order info 42", args.fill("/order info <order_id>"));
        Assertions.assertEquals("/company invites", args.fill("/company invites"));
    }

    @Test
    void typeIdsAreUnique() {
        var ids = Arrays.stream(NotificationType.values())
                .map(NotificationType::id)
                .distinct()
                .count();

        Assertions.assertEquals(NotificationType.values().length, ids);
    }

    @Test
    void argumentsSurviveTheJsonRoundTrip() {
        var args = NotificationArgs.create()
                .text("player_name", "<red>Steve\"")
                .money("amount", "$5.00")
                .number("count", 3);

        var read = NotificationArgs.fromJson(args.toJson());

        Assertions.assertEquals(args.values(), read.values());
    }

    @Test
    void storedPlainMessagesAreReadAsText() {
        var read = NotificationArgs.fromJson("{\"message\":\"<red>Hello\",\"n\":{\"value\":\"1\",\"kind\":\"ODD\"}}");

        Assertions.assertEquals(
                new NotificationArgs.Arg("<red>Hello", NotificationArgs.Kind.TEXT),
                read.values().get(NotificationArgs.LEGACY_MESSAGE));
        Assertions.assertEquals(
                NotificationArgs.Kind.TEXT, read.values().get("n").kind());
        Assertions.assertThrows(IllegalArgumentException.class, () -> NotificationArgs.fromJson("[]"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> NotificationArgs.fromJson("{"));
    }

    @Test
    void typesAreFoundByIdAndLegacyIsZero() {
        Assertions.assertEquals(Optional.of(NotificationType.LEGACY), NotificationType.byId(0));
        Assertions.assertEquals(Optional.of(NotificationType.PROFILE_CHANGED), NotificationType.byId(30));
        Assertions.assertEquals(Optional.of(NotificationType.MONEY_GRANTED), NotificationType.byId(37));
        Assertions.assertEquals(Optional.of(NotificationType.PAYMENT_SHARED), NotificationType.byId(40));
        Assertions.assertEquals(Optional.of(NotificationType.ORDER_ALERT_DIGEST), NotificationType.byId(48));
        Assertions.assertEquals(
                NotificationType.values().length,
                Arrays.stream(NotificationType.values())
                        .mapToInt(NotificationType::id)
                        .distinct()
                        .count());
        Assertions.assertEquals(NotificationCategory.ALERTS, NotificationType.ORDER_ALERT.category());
        Assertions.assertEquals(Optional.empty(), NotificationType.byId(-1));
    }

    @Test
    void categoryBitsAreDistinctAndOnlySomeAreMutable() {
        var bits = Arrays.stream(NotificationCategory.values())
                .map(NotificationCategory::bit)
                .distinct()
                .count();

        Assertions.assertEquals(NotificationCategory.values().length, bits);
        Assertions.assertEquals(
                Set.of(
                        NotificationCategory.MEMBERS,
                        NotificationCategory.ORDERS,
                        NotificationCategory.PAYMENTS,
                        NotificationCategory.LEVEL,
                        NotificationCategory.ALERTS),
                Arrays.stream(NotificationCategory.values())
                        .filter(NotificationCategory::mutable)
                        .collect(Collectors.toSet()));
        var mask = NotificationCategory.mask(List.of(NotificationCategory.ORDERS, NotificationCategory.LEVEL));
        Assertions.assertTrue(NotificationCategory.LEVEL.in(mask));
        Assertions.assertFalse(NotificationCategory.PAYMENTS.in(mask));
    }
}
