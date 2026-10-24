/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.MarketDay;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The paged reads: market statistics, inbox, treasury log and applications; and the treasury members see.
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
    void theRankingCountsItsPagesAndShowsTheLastPageForOnePastIt() {
        for (var i = 0; i < 11; i++) world.company("Company " + i, Map.of(world.player("Owner " + i), List.of()));

        var first = read(kestrel, Actions.RANKINGS_PAGE, "{\"ranking\":\"orders\",\"range\":\"all\",\"page\":1}");
        var far =
                read(kestrel, Actions.RANKINGS_PAGE, "{\"ranking\":\"orders\",\"range\":\"all\",\"page\":2147483647}");

        assertEquals(2, first.data().path("pages").asInt());
        assertEquals(10, first.data().path("entries").size());
        assertEquals(2, far.data().path("page").asInt());
        assertEquals(2, far.data().path("entries").size());
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

    @Test
    void theApplicationsComeWithTheInvitesTheCompanySent() {
        var now = LocalDateTime.now();
        var invited = world.player("Ivy");
        var applicant = world.player("Fern");
        world.memberships
                .submitInvite(miners, invited, kestrel, now, now.plusDays(1))
                .join();
        world.memberships
                .submitApplication(miners, applicant, "Hi", now, now.plusDays(1), 5)
                .join();

        var page = read(kestrel, Actions.APPLICATIONS_LIST, "{}").data();

        assertEquals(
                "Fern", page.path("entries").get(0).path("player").path("name").asString());
        var invite = page.path("invites").get(0);
        assertEquals("Ivy", invite.path("player").path("name").asString());
        assertEquals("Kestrel_", invite.path("inviter").path("name").asString());
    }

    @Test
    void theTreasuryNamesTheShareAndTheServersLimits() {
        world.companySettings.treasury().maxPayoutShare(20);
        world.treasury.submitShare(miners, 30, kestrel, LocalDateTime.now()).join();
        var company =
                world.companies.retrieveCompanyById(miners).asFuture().join().orElseThrow();
        var profile = world.companies
                .retrieveCompanyProfile(company)
                .asFuture()
                .join()
                .orElseThrow();

        var treasury = world.views.treasury(profile, kestrel).orElseThrow();

        assertEquals(20, treasury.share());
        assertEquals(20, treasury.maxShare());
        assertEquals(1.0, treasury.minTransaction());
        assertTrue(world.views.treasury(profile, owner).isEmpty());
    }

    private void delivered(
            int order, LocalDateTime finished, Material material, int amount, double price, boolean exact) {
        var line = new HistorySnapshot.Line(new ItemStack(material), exact, amount, amount, price);
        world.history
                .submitEntry(new HistoryEntry(
                        0,
                        order,
                        null,
                        miners,
                        HistoryOutcome.DELIVERED,
                        "Order " + order,
                        finished.minusDays(1),
                        finished,
                        new HistorySnapshot(List.of(line), price, 0, price, 0, price, List.of())))
                .join();
    }

    @Test
    void theMarketHistoryIsRecordedPerDayWithoutExactLinesAndShowsItsChange() {
        var today = world.market.today().atTime(12, 0);
        for (var i = 0; i < 3; i++) {
            world.state(world.order(owner, "Order " + i), OrderState.RECEIVED, miners);
            delivered(100 + i, today.minusDays(8), Material.STONE, 10, 100.0, false);
            delivered(200 + i, today, Material.STONE, 10, 120.0, false);
        }
        delivered(300, today, Material.STONE, 10, 500.0, true);

        world.market.record();
        world.market.record();

        var stone = read(kestrel, Actions.MARKET_ITEM, "{\"material\":\"minecraft:stone\",\"days\":30}")
                .data();
        var history = stone.path("history");
        assertEquals(2, history.size());
        assertEquals(
                today.minusDays(8).toLocalDate().toString(),
                history.get(0).path("day").asString());
        assertEquals(3, history.get(0).path("orders").asInt());
        assertEquals(10.0, history.get(0).path("avg").asDouble());
        assertEquals(3, history.get(1).path("orders").asInt());
        assertEquals(12.0, history.get(1).path("avg").asDouble());
        assertFalse(history.get(1).has("max"));
        assertEquals(30, stone.path("days").asInt());

        var movers = read(kestrel, Actions.MARKET_MOVERS, "{\"page\":1}").data().path("items");
        assertEquals(1, movers.size());
        assertEquals(20.0, movers.get(0).path("change").asDouble(), 0.0001);
    }

    @Test
    void purgingTheOrderHistoryKeepsTheRecordedDays() {
        var today = world.market.today().atTime(12, 0);
        for (var i = 0; i < 3; i++) delivered(500 + i, today.minusDays(5), Material.STONE, 10, 100.0, false);
        world.market.record();
        assertEquals(
                1, world.dashboard.marketDays(today.toLocalDate().minusDays(30)).size());

        world.history.submitPurge(today.plusDays(1)).join();
        world.orderHistoryDays = 1;
        world.market.record();
        world.market.record();

        assertEquals(
                1, world.dashboard.marketDays(today.toLocalDate().minusDays(30)).size());
        assertEquals(Optional.of(today.toLocalDate()), world.dashboard.marketRecordedThrough());
    }

    @Test
    void aDayWithTooFewOrdersShowsNoPrices() {
        var today = world.market.today().atTime(12, 0);
        world.state(world.order(owner, "Only"), OrderState.RECEIVED, miners);
        delivered(400, today, Material.STONE, 10, 100.0, false);

        world.market.record();

        var point = read(kestrel, Actions.MARKET_ITEM, "{\"material\":\"STONE\"}")
                .data()
                .path("history")
                .get(0);
        assertEquals(1, point.path("orders").asInt());
        assertTrue(point.path("avg").isMissingNode() || point.path("avg").isNull());
    }

    @Test
    void daysRecordedBeforeAPurgedStretchStayAfterALongPause() {
        var today = world.market.today();
        var stored = today.minusDays(15);
        world.dashboard.saveMarketDay(stored, List.of(new MarketDay(stored, "STONE", 3, 30, 10.0, 9.0, 11.0)));
        world.dashboard.saveMarketRecordedThrough(today.minusDays(20));
        world.history.submitPurge(today.plusDays(1).atStartOfDay()).join();
        world.orderHistoryDays = 7;

        world.market.record();

        assertEquals(
                1, world.dashboard.marketHistory("STONE", today.minusDays(30)).size());
        assertEquals(Optional.of(today), world.dashboard.marketRecordedThrough());
    }
}
