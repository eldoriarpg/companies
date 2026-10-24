/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.feed;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.dashboard.RecordingLink;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.PatchOp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchFeedTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @TempDir
    Path directory;

    private final RecordingLink link = new RecordingLink();
    private DashboardWorld world;
    private PatchFeed feed;
    private UUID owner;
    private UUID kestrel;
    private UUID wren;
    private UUID outsider;
    private int miners;
    private int farmers;
    private SimpleOrder order;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        feed = new PatchFeed(world.views, link, NODE, Runnable::run, Logger.getAnonymousLogger());
        owner = world.player("Ottilie");
        kestrel = world.player("Kestrel_");
        wren = world.player("Wren");
        outsider = world.player("Xavi");
        miners = world.company("Miners", Map.of(kestrel, List.of(CompanyPermission.OWNER), wren, List.of()));
        farmers = world.company("Farmers", Map.of(outsider, List.of(CompanyPermission.OWNER)));
        order = world.order(owner, "Market stalls");
        world.claim(order, miners);
        feed.subscribe("owner", owner, Optional.empty());
        feed.subscribe("kestrel", kestrel, Optional.of(miners));
        feed.subscribe("wren", wren, Optional.of(miners));
        feed.subscribe("outsider", outsider, Optional.of(farmers));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private List<Patch> patches(String path) {
        return link.of(Patch.class).stream()
                .filter(patch -> patch.path().equals(path))
                .toList();
    }

    private static String json(Object value) {
        return Protocol.mapper().writeValueAsString(value);
    }

    @Test
    void anOrderChangeReachesItsOwnerAndItsCompanyEachWithoutTheOtherSide() {
        world.deliver(order, wren, 4);

        feed.orderChanged(order, null);

        var own = patches("orders");
        var claimed = patches("company.orders");
        assertEquals(1, own.size());
        assertEquals(List.of("owner"), own.getFirst().sessions());
        assertEquals(PatchOp.UPSERT, own.getFirst().op());
        assertEquals(String.valueOf(order.id()), own.getFirst().key());
        assertFalse(json(own.getFirst()).contains(wren.toString()));
        assertFalse(json(own.getFirst()).contains("\"company\""));
        assertEquals(1, claimed.size());
        assertEquals(
                List.of("kestrel", "wren"),
                claimed.getFirst().sessions().stream().sorted().toList());
        assertFalse(json(claimed.getFirst()).contains(owner.toString()));
        assertEquals(
                4,
                claimed.getFirst()
                        .value()
                        .path("lines")
                        .get(0)
                        .path("delivered")
                        .asInt());
    }

    @Test
    void aDeliveryShowsTheCompanyWhichMemberDeliveredAndTheOwnerNothingOfIt() {
        world.deliver(order, wren, 4);

        feed.orderChanged(order, null);
        feed.memberChanged(miners, wren);

        var members = patches("company.members");
        assertEquals(1, members.size());
        assertEquals(
                List.of("kestrel", "wren"),
                members.getFirst().sessions().stream().sorted().toList());
        assertEquals(PatchOp.UPSERT, members.getFirst().op());
        assertEquals(wren.toString(), members.getFirst().key());
        assertEquals("Wren", members.getFirst().value().path("name").asString());
        assertEquals(4, members.getFirst().value().path("stats").path("items").asInt());
        assertEquals(
                4,
                patches("company.orders")
                        .getFirst()
                        .value()
                        .path("lines")
                        .get(0)
                        .path("delivered")
                        .asInt());
        assertTrue(link.of(Patch.class).stream()
                .filter(patch -> patch.sessions().contains("owner"))
                .noneMatch(patch -> json(patch).contains("Wren") || json(patch).contains(wren.toString())));
    }

    @Test
    void theInvitedPlayerSeesTheOpenInvites() {
        var now = LocalDateTime.now();
        world.memberships
                .submitInvite(miners, outsider, kestrel, now, now.plusHours(1))
                .join();

        feed.invitesChanged(outsider);

        var invites = patches("player.invites");
        assertEquals(1, invites.size());
        assertEquals(List.of("outsider"), invites.getFirst().sessions());
        assertEquals(PatchOp.REPLACE, invites.getFirst().op());
        assertEquals(miners, invites.getFirst().value().get(0).path("company").asInt());
        assertEquals("Miners", invites.getFirst().value().get(0).path("name").asString());
    }

    @Test
    void theRankingIsSentAgainWithEachViewersPlace() {
        feed.rankingsChanged();

        var rankings = patches("rankings");
        assertEquals(4, rankings.size());
        assertTrue(rankings.stream().allMatch(patch -> patch.op() == PatchOp.REPLACE));
        assertTrue(rankings.stream().allMatch(patch -> patch.sessions().size() == 1));
    }

    @Test
    void aMemberWhoJoinedIsAddedForTheOthersAndGetsTheCompany() {
        var newcomer = world.player("Nova");
        feed.subscribe("newcomer", newcomer, Optional.empty());
        world.companies
                .submitMemberUpdate(CompanyMember.of(miners, newcomer, 0))
                .join();

        feed.memberJoined(miners, newcomer);

        var members = patches("company.members");
        assertEquals(
                List.of("kestrel", "wren"),
                members.getFirst().sessions().stream().sorted().toList());
        assertEquals(newcomer.toString(), members.getFirst().key());
        var company = patches("company");
        assertEquals(List.of("newcomer"), company.getFirst().sessions());
        assertEquals(PatchOp.REPLACE, company.getFirst().op());
        feed.orderChanged(order, null);
        assertTrue(patches("company.orders").getFirst().sessions().contains("newcomer"));
    }

    @Test
    void anOrderTheCompanyLostIsRemovedFromItsList() {
        world.orders.submitUnclaimOrder(order).join();
        world.orders.forgetFullOrder(order.id());

        feed.orderChanged(order, miners);

        var removed = patches("company.orders");
        assertEquals(1, removed.size());
        assertEquals(PatchOp.REMOVE, removed.getFirst().op());
        assertEquals(
                List.of("kestrel", "wren"),
                removed.getFirst().sessions().stream().sorted().toList());
        assertEquals(PatchOp.UPSERT, patches("orders").getFirst().op());
    }

    @Test
    void theTreasuryReachesMembersWithTheTreasuryPermissionOnly() {
        feed.treasuryChanged(miners);

        var treasury = patches("company.treasury");
        assertEquals(1, treasury.size());
        assertEquals(List.of("kestrel"), treasury.getFirst().sessions());
    }

    @Test
    void aCompanyChangeIsBuiltPerViewer() {
        feed.companyChanged(miners);

        var company = patches("company");
        assertEquals(2, company.size());
        var forKestrel = company.stream()
                .filter(patch -> patch.sessions().equals(List.of("kestrel")))
                .findFirst();
        var forWren = company.stream()
                .filter(patch -> patch.sessions().equals(List.of("wren")))
                .findFirst();
        assertTrue(forKestrel.orElseThrow().value().has("treasury"));
        assertFalse(forWren.orElseThrow().value().has("treasury"));
        assertTrue(
                patches("player").stream().noneMatch(patch -> patch.sessions().contains("owner")));
    }

    @Test
    void aPlayerWhoLeftLosesTheCompanyAndTheOthersTheMember() {
        world.companies
                .submitMemberUpdate(CompanyMember.of(miners, wren, 0).kick())
                .join();

        feed.memberLeft(miners, wren);

        var company = patches("company");
        assertEquals(1, company.size());
        assertEquals(PatchOp.REMOVE, company.getFirst().op());
        assertEquals(List.of("wren"), company.getFirst().sessions());
        var members = patches("company.members");
        assertEquals(List.of("kestrel"), members.getFirst().sessions());
        assertEquals(PatchOp.REMOVE, members.getFirst().op());
        feed.orderChanged(order, null);
        assertEquals(List.of("kestrel"), patches("company.orders").getFirst().sessions());
    }

    @Test
    void theInboxCountReachesThePlayerOnly() {
        feed.inboxChanged(wren);

        var inbox = patches("player.unreadInbox");
        assertEquals(1, inbox.size());
        assertEquals(List.of("wren"), inbox.getFirst().sessions());
        assertEquals(0, inbox.getFirst().value().asInt());
    }

    @Test
    void patchesAreNumberedAndUnsubscribedSessionsGetNone() {
        feed.unsubscribe("owner");
        feed.orderChanged(order, null);
        feed.inboxChanged(wren);

        var patches = link.of(Patch.class);
        assertTrue(patches.stream().noneMatch(patch -> patch.sessions().contains("owner")));
        assertEquals(List.of(1L, 2L), patches.stream().map(Patch::seq).toList());
        assertEquals(2, feed.sequence());
        assertTrue(patches.stream().allMatch(patch -> patch.node().equals(NODE)));
    }

    @Test
    void withoutSubscriptionsNothingIsBuilt() {
        feed.clear();
        feed.orderChanged(order, null);

        assertTrue(link.all().isEmpty());
        assertEquals(0, feed.sequence());
    }
}
