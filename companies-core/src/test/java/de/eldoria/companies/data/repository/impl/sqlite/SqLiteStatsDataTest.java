/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.stats.MemberStats;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Three companies: Miners and Farmers earned in the last days, Smiths never completed an order but has the most
 * members. Miners earned more than Farmers in the last 30 days, Farmers more in the last 7 days.
 */
class SqLiteStatsDataTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0, 0);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private SqLiteStatsData stats;
    private int miners;
    private int farmers;
    private int smiths;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        stats = new SqLiteStatsData(executor);
        miners = createCompany("Miners", 2, alice, bob);
        farmers = createCompany("Farmers", 2, carol);
        smiths = createCompany("Smiths", 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var minersCompany = companyData.retrieveCompanyById(miners).join().orElseThrow();
        companyData
                .submitDetails(minersCompany, CompanyDetails.NONE.withTag("min").withColor("gold"))
                .join();

        Assertions.assertTrue(stats.submitPayouts(List.of(
                        payout(1, miners, alice, 10, 100, true, NOW.minusDays(2)),
                        payout(1, miners, bob, 5, 50, false, NOW.minusDays(2)),
                        payout(2, miners, alice, 4, 60, true, NOW.minusDays(20)),
                        payout(3, miners, alice, 100, 1000, true, NOW.minusDays(60)),
                        payout(4, farmers, carol, 20, 200, true, NOW.minusDays(1))))
                .join());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private int createCompany(String name, int level, UUID... members) {
        var id = companyData.submitCompanyCreation(name, JoinMode.OPEN).join();
        for (var member : members) {
            companyData
                    .submitMemberUpdate(
                            CompanyMember.of(id, member, member == members[0] ? CompanyPermission.OWNER.mask() : 0))
                    .join();
        }
        var company = companyData.retrieveCompanyById(id).join().orElseThrow();
        companyData.submitCompanyLevelUpdate(company, level).join();
        return id;
    }

    private static PayoutRecord payout(
            int order, int company, UUID worker, int items, double gross, boolean paid, LocalDateTime created) {
        return new PayoutRecord(order, company, worker, items, gross, gross * 0.1, gross * 0.9, paid, created);
    }

    private List<Integer> ranking(TopOrder order, TimeRange range) {
        return stats.retrieveRanking(order, range, NOW, 1, 10).join().stream()
                .map(CompanyRank::id)
                .toList();
    }

    @Test
    void rollingWindowsRankOnlyTheirPayouts() {
        Assertions.assertEquals(List.of(farmers, miners), ranking(TopOrder.EARNED, TimeRange.LAST_7_DAYS));
        Assertions.assertEquals(List.of(miners, farmers), ranking(TopOrder.EARNED, TimeRange.LAST_30_DAYS));
        Assertions.assertEquals(List.of(farmers, miners), ranking(TopOrder.ITEMS_DELIVERED, TimeRange.LAST_30_DAYS));
    }

    @Test
    void rankedValuesComeFromTheWindow() {
        var ranks = stats.retrieveRanking(TopOrder.EARNED, TimeRange.LAST_30_DAYS, NOW, 1, 10)
                .join();
        var minersRank = ranks.getFirst();

        Assertions.assertEquals(1, minersRank.rank());
        Assertions.assertEquals(210.0, minersRank.price(), 1e-9);
        Assertions.assertEquals(19, minersRank.deliveredItems());
        Assertions.assertEquals(2, minersRank.orderCount());
        Assertions.assertEquals(2, minersRank.memberCount());
        Assertions.assertEquals(2, minersRank.level());
        Assertions.assertEquals("MIN", minersRank.tag().orElseThrow());
        Assertions.assertEquals("gold", minersRank.color().orElseThrow());
        Assertions.assertEquals("Miners", minersRank.name());
        Assertions.assertTrue(ranks.get(1).tag().isEmpty());
    }

    @Test
    void theWindowEndsAtTheGivenTime() {
        var later = NOW.plusDays(6);

        var ranks = stats.retrieveRanking(TopOrder.EARNED, TimeRange.LAST_7_DAYS, later, 1, 10)
                .join();

        Assertions.assertEquals(
                List.of(farmers), ranks.stream().map(CompanyRank::id).toList());
    }

    @Test
    void companiesWithoutActivityAreOnlyRankedAllTime() {
        Assertions.assertFalse(ranking(TopOrder.MEMBER, TimeRange.LAST_30_DAYS).contains(smiths));
        Assertions.assertEquals(List.of(smiths, miners, farmers), ranking(TopOrder.MEMBER, TimeRange.ALL));
    }

    @Test
    void tiesAreBrokenByTheCompanyId() {
        Assertions.assertEquals(List.of(miners, farmers), ranking(TopOrder.ORDERS, TimeRange.LAST_7_DAYS));
        Assertions.assertEquals(List.of(miners, farmers, smiths), ranking(TopOrder.ORDERS, TimeRange.ALL));
    }

    @Test
    void levelTiesAreBrokenByTheMoneyEarnedInTheRange() {
        Assertions.assertEquals(List.of(farmers, miners), ranking(TopOrder.LEVEL, TimeRange.LAST_7_DAYS));
        Assertions.assertEquals(List.of(miners, farmers), ranking(TopOrder.LEVEL, TimeRange.LAST_30_DAYS));
        Assertions.assertEquals(smiths, ranking(TopOrder.LEVEL, TimeRange.ALL).getLast());
    }

    @Test
    void allTimeRanksCompaniesWithoutOrdersLast() {
        insertCompletedOrder(farmers, 500.0, 7);

        var ranks = stats.retrieveRanking(TopOrder.EARNED, TimeRange.ALL, NOW, 1, 10)
                .join();

        Assertions.assertEquals(
                List.of(farmers, miners, smiths),
                ranks.stream().map(CompanyRank::id).toList());
        Assertions.assertEquals(500.0, ranks.getFirst().price(), 1e-9);
        Assertions.assertEquals(7, ranks.getFirst().deliveredItems());
        Assertions.assertEquals(0.0, ranks.get(1).price(), 1e-9);
    }

    @Test
    void pagesContinueTheNumbering() {
        var second = stats.retrieveRanking(TopOrder.EARNED, TimeRange.LAST_30_DAYS, NOW, 2, 1)
                .join();

        Assertions.assertEquals(1, second.size());
        Assertions.assertEquals(farmers, second.getFirst().id());
        Assertions.assertEquals(2, second.getFirst().rank());
        Assertions.assertEquals(
                List.of(),
                stats.retrieveRanking(TopOrder.EARNED, TimeRange.LAST_30_DAYS, NOW, 3, 1)
                        .join());
    }

    @Test
    void theOwnRankIsFoundOrEmptyWhenNotRanked() {
        Assertions.assertEquals(
                2,
                stats.retrieveRank(miners, TopOrder.EARNED, TimeRange.LAST_7_DAYS, NOW)
                        .join()
                        .orElseThrow()
                        .rank());
        Assertions.assertTrue(stats.retrieveRank(smiths, TopOrder.EARNED, TimeRange.LAST_7_DAYS, NOW)
                .join()
                .isEmpty());
        Assertions.assertEquals(
                1,
                stats.retrieveRank(smiths, TopOrder.MEMBER, TimeRange.ALL, NOW)
                        .join()
                        .orElseThrow()
                        .rank());
    }

    @Test
    void theRankIndexHoldsEveryCompanyAllTime() {
        Assertions.assertEquals(
                Map.of(smiths, 1, miners, 2, farmers, 3),
                stats.retrieveRankIndex(TopOrder.MEMBER).join());
    }

    @Test
    void memberStatsCountUnpaidPayoutsWithoutMoney() {
        var all = byWorker(stats.retrieveMemberStats(miners, null).join());

        Assertions.assertEquals(2, all.size());
        Assertions.assertEquals(114, all.get(alice).items());
        Assertions.assertEquals(1044.0, all.get(alice).earned(), 1e-9);
        Assertions.assertEquals(3, all.get(alice).orders());
        Assertions.assertEquals(5, all.get(bob).items());
        Assertions.assertEquals(0.0, all.get(bob).earned(), 1e-9);
        Assertions.assertEquals(1, all.get(bob).orders());
    }

    @Test
    void memberStatsOfARange() {
        var recent = stats.retrieveMemberStats(miners, NOW.minusDays(30)).join();

        Assertions.assertEquals(alice, recent.getFirst().worker());
        var aliceStats = byWorker(recent).get(alice);
        Assertions.assertEquals(14, aliceStats.items());
        Assertions.assertEquals(144.0, aliceStats.earned(), 1e-9);
        Assertions.assertEquals(2, aliceStats.orders());
        Assertions.assertEquals(
                List.of(), stats.retrieveMemberStats(smiths, null).join());
    }

    @Test
    void purgingACompanyDeletesItsPayouts() {
        var company = companyData.retrieveCompanyById(miners).join().orElseThrow();

        Assertions.assertTrue(companyData.submitCompanyPurge(company).join());

        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_payouts", "company_id = " + miners));
        Assertions.assertEquals(1, SqLiteTestDatabase.count("order_payouts", "company_id = " + farmers));
    }

    @Test
    void theTreasuryShareIsStoredAndDefaultsToZero() {
        Assertions.assertTrue(
                stats.submitPayouts(List.of(new PayoutRecord(9, farmers, carol, 1, 100, 10, 9, 81, true, NOW)))
                        .join());

        Assertions.assertEquals(1, SqLiteTestDatabase.count("order_payouts", "order_id = 9 AND share = 9"));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_payouts", "order_id <> 9 AND share <> 0"));
    }

    @Test
    void anEmptyBatchIsWritten() {
        Assertions.assertTrue(stats.submitPayouts(List.of()).join());
    }

    private static Map<UUID, MemberStats> byWorker(List<MemberStats> stats) {
        return stats.stream().collect(Collectors.toMap(MemberStats::worker, Function.identity()));
    }

    private static void insertCompletedOrder(int company, double price, int amount) {
        var id = query("INSERT INTO orders(owner_uuid, name) VALUES (?, 'done') RETURNING id")
                .single(call().bind(UUID.randomUUID(), UUID_BYTES))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query(
                        "INSERT INTO order_content(id, line, material, stack, exact, amount, price) VALUES (?, 0, 'STONE', '{}', 0, ?, ?)")
                .single(call().bind(id).bind(amount).bind(price))
                .insert();
        query("INSERT INTO order_states(id, company, state) VALUES (?, ?, 300)")
                .single(call().bind(id).bind(company))
                .insert();
    }
}
