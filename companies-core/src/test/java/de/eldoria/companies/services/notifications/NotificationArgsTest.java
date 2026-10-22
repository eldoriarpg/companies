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
}
