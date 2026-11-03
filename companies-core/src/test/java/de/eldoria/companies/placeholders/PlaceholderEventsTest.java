/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyAreasChangeEvent;
import de.eldoria.companies.events.company.CompanyCreateEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.events.company.CompanyOwnerTransferEvent;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.company.TreasuryTransactionEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The placeholder caches of this node follow its own events, for every change the other nodes are told to reload.
 */
class PlaceholderEventsTest {
    private static final int COMPANY = 7;
    private static final int BOARDS = TopOrder.values().length * TimeRange.values().length;

    private final Map<Object, AtomicInteger> loads = new ConcurrentHashMap<>();
    private final AtomicInteger rankings = new AtomicInteger();
    private ServerMock server;
    private PlayerPlaceholderCache players;
    private CompanyPlaceholderCache companies;
    private PlaceholderEvents events;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        players = new PlayerPlaceholderCache(
                uuid -> {
                    loads.computeIfAbsent(uuid, any -> new AtomicInteger()).incrementAndGet();
                    return CompletableFuture.completedFuture(Optional.empty());
                },
                10,
                () -> Duration.ofMinutes(5));
        companies = new CompanyPlaceholderCache(
                id -> {
                    loads.computeIfAbsent(id, any -> new AtomicInteger()).incrementAndGet();
                    return CompletableFuture.completedFuture(Optional.empty());
                },
                10,
                () -> Duration.ofMinutes(5));
        var leaderboards = new LeaderboardCache(
                (order, range) -> {
                    rankings.incrementAndGet();
                    return CompletableFuture.completedFuture(List.of());
                },
                () -> 10,
                Runnable::run);
        events = new PlaceholderEvents(
                players,
                companies,
                new OfferPlaceholderCache(() -> CompletableFuture.completedFuture(List.of())),
                leaderboards);
        companies.get(COMPANY);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void changedAreasReloadTheCompany() {
        events.onAreas(new CompanyAreasChangeEvent(company(), CompanyAreasChangeEvent.Change.AREAS));

        assertEquals(2, loads(COMPANY));
    }

    @Test
    void aFoundedCompanyReloadsItsFounder() {
        var founder = cached(server.addPlayer().getUniqueId());

        events.onCompanyCreate(new CompanyCreateEvent(company(), server.getOfflinePlayer(founder)));

        assertEquals(2, loads(founder));
    }

    @Test
    void aRenameReloadsTheCompanyAndTheRankings() {
        events.onCompanyRename(new CompanyRenameEvent(company(), "Diggers", null, false));

        assertEquals(2, loads(COMPANY));
        assertEquals(BOARDS, rankings.get());
    }

    @Test
    void changedPermissionsReloadTheMember() {
        var member = cached(UUID.randomUUID());

        events.onMemberPermission(new CompanyMemberPermissionEvent(
                company(),
                CompanyMember.of(COMPANY, member, CompanyPermission.INVITE.mask()),
                UUID.randomUUID(),
                Set.of()));

        assertEquals(2, loads(member));
    }

    @Test
    void anOwnerTransferReloadsTheCompanyAndBothOwners() {
        var previous = cached(server.addPlayer().getUniqueId());
        var next = cached(server.addPlayer().getUniqueId());

        events.onOwnerTransfer(new CompanyOwnerTransferEvent(
                company(), server.getOfflinePlayer(previous), server.getOfflinePlayer(next), false));

        assertEquals(2, loads(COMPANY));
        assertEquals(2, loads(previous));
        assertEquals(2, loads(next));
    }

    @Test
    void aTreasuryTransactionReloadsTheCompany() {
        events.onTreasuryTransaction(new TreasuryTransactionEvent(
                company(), TreasuryKind.DEPOSIT, BigDecimal.TEN, BigDecimal.TEN, UUID.randomUUID()));

        assertEquals(2, loads(COMPANY));
    }

    @Test
    void aMemberWhoJoinsReloadsThePlayerTheCompanyAndTheRankings() {
        var member = cached(server.addPlayer().getUniqueId());

        events.onCompanyJoin(new CompanyJoinEvent(company(), server.getOfflinePlayer(member)));

        assertEquals(2, loads(member));
        assertEquals(2, loads(COMPANY));
        assertEquals(BOARDS, rankings.get());
    }

    @Test
    void aProfileEditReloadsTheCompanyAndTheRankings() {
        events.onProfileEdit(
                new CompanyProfileEditEvent(company(), company(), server.getOfflinePlayer(UUID.randomUUID())));

        assertEquals(2, loads(COMPANY));
        assertEquals(BOARDS, rankings.get());
    }

    @Test
    void returnedItemsReloadThePlayerWhoGetsThem() {
        var player = cached(server.addPlayer().getUniqueId());

        events.onItemsReturned(new ItemsReturnedEvent(
                server.getOfflinePlayer(player), List.of(), ItemsReturnedEvent.Reason.ORDER_ABORTED, null));

        assertEquals(2, loads(player));
    }

    private UUID cached(UUID player) {
        players.join(player);
        return player;
    }

    private int loads(Object key) {
        return loads.getOrDefault(key, new AtomicInteger()).get();
    }

    private static CompanyProfile company() {
        return new CompanyProfile(COMPANY, "Miners", Times.now(), 1, CompanyDetails.NONE, List.of());
    }
}
