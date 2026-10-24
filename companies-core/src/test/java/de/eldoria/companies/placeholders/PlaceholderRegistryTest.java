/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderRegistryTest {
    private static final LocalDateTime FOUNDED = LocalDateTime.of(2026, 1, 2, 3, 4);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID LONER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private final Map<UUID, PlayerView> playerViews = new HashMap<>();
    private final Map<Integer, CompanyView> companyViews = new HashMap<>();
    private final Map<TimeRange, List<CompanyRank>> rankings = new HashMap<>();
    private PlayerPlaceholderCache players;
    private CompanyPlaceholderCache companies;
    private LeaderboardCache leaderboards;
    private PlaceholderRegistry registry;

    @BeforeEach
    void setUp() {
        playerViews.put(OWNER, new PlayerView(OWNER, OptionalInt.of(7), Optional.of(Role.OWNER), 2, 5, 12));
        playerViews.put(LONER, new PlayerView(LONER, OptionalInt.empty(), Optional.empty(), 0, 7, 0, 1, 4));
        companyViews.put(
                7,
                new CompanyView(
                        7,
                        "<red>Acme",
                        3,
                        "Guild",
                        4,
                        1,
                        "Owner",
                        Optional.of("ACME"),
                        Optional.of("#aabbcc"),
                        9,
                        1234.5,
                        640,
                        87.5,
                        15));
        rankings.put(TimeRange.ALL, List.of(rank(1, 3, "Best", "TOP", "gold"), rank(2, 7, "<red>Acme", "ACME", null)));
        rankings.put(TimeRange.LAST_7_DAYS, List.of(rank(1, 7, "<red>Acme", "ACME", null)));
        rankings.put(TimeRange.LAST_30_DAYS, List.of());
        players = new PlayerPlaceholderCache(
                uuid -> CompletableFuture.completedFuture(Optional.ofNullable(playerViews.get(uuid))),
                10,
                () -> Duration.ofMinutes(5));
        companies = new CompanyPlaceholderCache(
                id -> CompletableFuture.completedFuture(Optional.ofNullable(companyViews.get(id))),
                10,
                () -> Duration.ofMinutes(5));
        leaderboards =
                new LeaderboardCache((order, range) -> CompletableFuture.completedFuture(rankings.get(range)), 10);
        registry = new PlaceholderRegistry(
                players,
                companies,
                leaderboards,
                amount -> String.format(Locale.ROOT, "$%.2f", amount),
                key -> "t:" + key,
                () -> "...");
    }

    private static CompanyRank rank(int place, int id, String name, String tag, String color) {
        return new CompanyRank(place, id, name, FOUNDED, 3, 11 * id, 100.0 * id, 5 * id, id, tag, color);
    }

    private void loadAll() {
        players.join(OWNER).join();
        players.join(LONER).join();
        companies.get(7);
        leaderboards.refresh().join();
    }

    @Test
    void anUnknownPlayerReadsTheLoadingTextFirst() {
        assertEquals("...", registry.resolve(OWNER, "player_open_orders"));
        assertEquals("...", registry.resolve(OWNER, "company_name"));
        assertEquals("<red>Acme", registry.resolve(OWNER, "company_name"));
    }

    @Test
    void companyIdsDescribeThePlayersCompany() {
        loadAll();

        assertEquals("<red>Acme", registry.resolve(OWNER, "company_name"));
        assertEquals("4", registry.resolve(OWNER, "company_size"));
        assertEquals("3", registry.resolve(OWNER, "company_level"));
        assertEquals("1", registry.resolve(OWNER, "active_orders"));
        assertEquals("ACME", registry.resolve(OWNER, "company_tag"));
        assertEquals("§x§a§a§b§b§c§cACME", registry.resolve(OWNER, "company_tag_colored"));
        assertEquals("#aabbcc", registry.resolve(OWNER, "company_color"));
        assertEquals("Guild", registry.resolve(OWNER, "company_level_name"));
        assertEquals("Owner", registry.resolve(OWNER, "company_owner"));
        assertEquals("$1234.50", registry.resolve(OWNER, "company_earned"));
        assertEquals("1234.50", registry.resolve(OWNER, "company_earned_raw"));
        assertEquals("640", registry.resolve(OWNER, "company_delivered"));
        assertEquals("9", registry.resolve(OWNER, "company_orders"));
        assertEquals("$87.50", registry.resolve(OWNER, "company_treasury"));
        assertEquals("15", registry.resolve(OWNER, "company_payout_share"));
    }

    @Test
    void companyRanksReadEveryRange() {
        loadAll();

        assertEquals("2", registry.resolve(OWNER, "company_rank_orders"));
        assertEquals("2", registry.resolve(OWNER, "company_rank_items_delivered_all"));
        assertEquals("1", registry.resolve(OWNER, "company_rank_earned_7d"));
        assertEquals("", registry.resolve(OWNER, "company_rank_level_30d"));
        assertNull(registry.resolve(OWNER, "company_rank_fame"));
        assertNull(registry.resolve(OWNER, "company_rank_orders_week"));
    }

    @Test
    void playerIdsDescribeThePlayer() {
        loadAll();

        assertEquals("2", registry.resolve(OWNER, "player_open_orders"));
        assertEquals("5", registry.resolve(OWNER, "player_order_limit"));
        assertEquals("12", registry.resolve(OWNER, "player_items_waiting"));
        assertEquals("t:placeholder.role.owner", registry.resolve(OWNER, "player_role"));
        assertEquals("", registry.resolve(LONER, "player_role"));
    }

    @Test
    void soloIdsCountTheOrdersWorkedOnAlone() {
        loadAll();

        assertEquals("1", registry.resolve(LONER, "player_solo_active"));
        assertEquals("4", registry.resolve(LONER, "player_solo_completed"));
        assertEquals("0", registry.resolve(OWNER, "player_solo_active"));
        assertEquals("0", registry.resolve(OWNER, "player_solo_completed"));
    }

    @Test
    void withoutCompanyNamesAreEmptyAndCountsZero() {
        loadAll();

        assertEquals("", registry.resolve(LONER, "company_name"));
        assertEquals("", registry.resolve(LONER, "company_tag_colored"));
        assertEquals("0", registry.resolve(LONER, "company_size"));
        assertEquals("0", registry.resolve(LONER, "active_orders"));
        assertEquals("", registry.resolve(LONER, "company_rank_orders"));
        assertEquals("", registry.resolve(null, "company_name"));
        assertEquals("", registry.resolve(null, "player_role"));
    }

    @Test
    void leaderboardIdsAreParsed() {
        loadAll();

        assertEquals("Best", registry.resolve(null, "top_orders_1_name"));
        assertEquals("TOP", registry.resolve(null, "top_orders_1_tag"));
        assertEquals("§6TOP", registry.resolve(null, "top_orders_1_tag_colored"));
        assertEquals("33", registry.resolve(null, "top_orders_1_value"));
        assertEquals("$700.00", registry.resolve(null, "top_earned_all_2_value"));
        assertEquals("35", registry.resolve(null, "top_items_delivered_2_value"));
        assertEquals("7", registry.resolve(null, "top_level_7d_1_value"));
        assertEquals("<red>Acme", registry.resolve(null, "TOP_MEMBER_7D_1_NAME"));
        assertEquals("", registry.resolve(null, "top_orders_7d_2_name"));
        assertEquals("", registry.resolve(null, "top_orders_30d_1_name"));
    }

    @Test
    void malformedLeaderboardIdsAreUnknown() {
        loadAll();

        assertNull(registry.resolve(null, "top_orders_1_owner"));
        assertNull(registry.resolve(null, "top_orders_0_name"));
        assertNull(registry.resolve(null, "top_orders_x_name"));
        assertNull(registry.resolve(null, "top_fame_1_name"));
        assertNull(registry.resolve(null, "top_orders_week_1_name"));
        assertNull(registry.resolve(null, "top_name"));
    }

    @Test
    void leaderboardsReadTheLoadingTextUntilLoaded() {
        assertEquals("...", registry.resolve(null, "top_orders_1_name"));
    }

    @Test
    void unknownIdsResolveToNull() {
        loadAll();

        assertNull(registry.resolve(OWNER, "company_secret"));
        assertNull(registry.resolve(OWNER, "order_owner"));
        assertNull(registry.resolve(OWNER, ""));
    }

    @Test
    void everyFixedIdIsListed() {
        loadAll();
        List<String> fixed = new ArrayList<>();
        for (var id : registry.ids()) if (!id.contains("<")) fixed.add(id);

        assertEquals(21, fixed.size());
        for (var id : fixed) assertFalse(registry.resolve(OWNER, id) == null, id);
        assertTrue(registry.ids().contains("company_rank_<order>[_<range>]"));
    }

    @Test
    void aStrangerIsLoadedOnFirstRead() {
        playerViews.put(STRANGER, new PlayerView(STRANGER, OptionalInt.empty(), Optional.empty(), 1, 7, 0));

        assertEquals("...", registry.resolve(STRANGER, "player_open_orders"));
        assertEquals("1", registry.resolve(STRANGER, "player_open_orders"));
    }
}
