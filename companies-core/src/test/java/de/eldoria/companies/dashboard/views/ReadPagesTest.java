/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The paged reads: market statistics, inbox and treasury log.
 */
class ReadPagesTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID owner;
    private UUID kestrel;
    private int miners;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        owner = world.player("Ottilie");
        kestrel = world.player("Kestrel_");
        miners = world.company("Miners", Map.of(kestrel, List.of(CompanyPermission.OWNER)));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult read(UUID player, String action, String args) {
        var now = LocalDateTime.now();
        var session = new StoredSession("session", player, "name", NODE, now, now, now, now, null, Set.of());
        var request = new ActionRequest(
                "read",
                Protocol.VERSION,
                "session",
                player,
                action,
                Protocol.mapper().readTree(args));
        return world.reads.answer(request, session, feature -> true);
    }

    @Test
    void theMarketShowsPricesOnlyFromTheMinimumOfOrdersOn() {
        for (var i = 0; i < 3; i++) world.state(world.order(owner, "Order " + i), OrderState.RECEIVED, miners);
        world.state(world.order(owner, "Open"), OrderState.UNCLAIMED, 0);

        var top = read(kestrel, Actions.MARKET_TOP, "{\"page\":1}");
        var search = read(kestrel, Actions.MARKET_SEARCH, "{\"query\":\"sto\",\"page\":1}");

        var items = top.data().path("items");
        assertEquals(2, items.size());
        var stone = items.get(0);
        assertEquals("minecraft:stone", stone.path("item").path("material").asString());
        assertEquals(30, stone.path("volume").asInt());
        assertEquals(10.0, stone.path("avg").asDouble());
        assertFalse(stone.has("change"));
        assertEquals(1, search.data().path("total").asInt());
        assertEquals(
                "Stone",
                search.data().path("items").get(0).path("item").path("name").asString());

        var dirt = read(kestrel, Actions.MARKET_ITEM, "{\"material\":\"minecraft:dirt\"}");
        assertTrue(dirt.ok());
        assertEquals(10.0, dirt.data().path("avg").asDouble());
    }

    @Test
    void pricesOfRarelyOrderedMaterialsAreLeftOut() {
        world.state(world.order(owner, "Only"), OrderState.DELIVERED, miners);

        var item = read(kestrel, Actions.MARKET_ITEM, "{\"material\":\"STONE\"}");

        assertTrue(item.ok());
        assertFalse(item.data().has("avg"));
        assertFalse(item.data().has("min"));
        assertEquals(1, item.data().path("orders").asInt());
    }

    @Test
    void theInboxIsPagedSearchedAndStaysUnread() {
        for (var i = 0; i < 3; i++) {
            world.inbox
                    .submit(
                            owner,
                            NotificationType.LEGACY,
                            NotificationArgs.create().text(NotificationArgs.LEGACY_MESSAGE, "Message " + i),
                            LocalDateTime.now(),
                            InboxStatus.PENDING)
                    .join();
        }

        var page = read(owner, Actions.INBOX_PAGE, "{\"page\":2,\"pageSize\":2}");

        assertEquals(2, page.data().path("pages").asInt());
        assertEquals(3, page.data().path("total").asInt());
        assertEquals(1, page.data().path("entries").size());
        assertEquals("LEGACY", page.data().path("entries").get(0).path("type").asString());
        assertFalse(page.data().path("entries").get(0).path("read").asBoolean());
        assertEquals(3, world.views.unreadInbox(owner));
    }

    @Test
    void theTreasuryLogNamesTheActorAndPages() {
        var now = LocalDateTime.now();
        world.treasury
                .submitCredit(miners, 1050, new TreasuryEntry(TreasuryKind.DEPOSIT, kestrel, null, null, now))
                .join();
        world.treasury
                .submitCredit(miners, 200, new TreasuryEntry(TreasuryKind.DEPOSIT, kestrel, null, null, now))
                .join();

        var log = read(kestrel, Actions.TREASURY_LOG, "{\"page\":1,\"pageSize\":1}");

        assertEquals(2, log.data().path("pages").asInt());
        var entry = log.data().path("entries").get(0);
        assertEquals("Kestrel_", entry.path("actor").path("name").asString());
        assertTrue(entry.has("time"));
        assertTrue(entry.path("amount").asDouble() > 0);
    }
}
