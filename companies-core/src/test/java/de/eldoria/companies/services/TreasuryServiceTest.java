/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.configuration.elements.companysettings.TreasurySettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.events.company.TreasuryTransactionEvent;
import de.eldoria.companies.services.TreasuryService.Outcome;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryServiceTest {
    private final Map<UUID, BigDecimal> wallets = new ConcurrentHashMap<>();
    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<Notice> notices = new CopyOnWriteArrayList<>();
    private final AtomicBoolean vaultRefusesDeposits = new AtomicBoolean();
    private final AtomicBoolean creditsFail = new AtomicBoolean();
    private final TreasurySettings settings = new TreasurySettings();

    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private SqLiteTreasuryData data;
    private TreasuryService service;
    private OfflinePlayer owner;
    private OfflinePlayer treasurer;
    private OfflinePlayer worker;
    private OfflinePlayer stranger;
    private CompanyProfile company;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        data = new SqLiteTreasuryData(executor) {
            @Override
            protected Optional<Long> credit(int company, long cents, TreasuryEntry entry) {
                if (creditsFail.get()) return Optional.empty();
                return super.credit(company, cents, entry);
            }
        };
        service = new TreasuryService(
                data,
                new FakeWallet(),
                () -> settings,
                (recipient, type, args) -> notices.add(new Notice(recipient, type, args)),
                events::add,
                Runnable::run,
                Clock.systemDefaultZone(),
                Logger.getLogger("TreasuryServiceTest"));
        owner = player(0);
        treasurer = player(100);
        worker = player(100);
        stranger = player(100);
        var id = query("INSERT INTO companies(name) VALUES ('Miners') RETURNING id")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        company = new CompanyProfile(
                id,
                "Miners",
                LocalDateTime.now(),
                1,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(id, owner.getUniqueId(), CompanyPermission.OWNER.mask()),
                        CompanyMember.of(id, treasurer.getUniqueId(), CompanyPermission.TREASURY.mask()),
                        CompanyMember.of(id, worker.getUniqueId(), 0)));
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private OfflinePlayer player(int money) {
        var player = server.getOfflinePlayer(UUID.randomUUID());
        wallets.put(player.getUniqueId(), BigDecimal.valueOf(money));
        return player;
    }

    private BigDecimal wallet(OfflinePlayer player) {
        return wallets.get(player.getUniqueId()).stripTrailingZeros();
    }

    private long balance() {
        return data.retrieveTreasury(company.id()).join().cents();
    }

    private List<TreasuryKind> logKinds() {
        return data.retrieveLog(company.id(), 1, 100).join().stream()
                .map(TreasuryLogEntry::kind)
                .toList();
    }

    private void fund(long cents) {
        assertTrue(service.adminAdjust(company, owner.getUniqueId(), true, BigDecimal.valueOf(cents, 2))
                .join()
                .done());
        events.clear();
    }

    private void assertMoney(String expected, OfflinePlayer player) {
        assertEquals(0, amount(expected).compareTo(wallet(player)), () -> expected + " != " + wallet(player));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @Test
    void depositMovesMoneyFromTheMemberIntoTheTreasury() {
        var result = service.deposit(company, worker, amount("40.25")).join();

        assertEquals(Outcome.DONE, result.outcome());
        assertEquals(4025, result.cents());
        assertMoney("59.75", worker);
        assertEquals(4025, balance());
        var event = (TreasuryTransactionEvent) events.get(0);
        assertEquals(TreasuryKind.DEPOSIT, event.kind());
        assertEquals(amount("40.25"), event.amount());
        assertEquals(Optional.of(worker.getUniqueId()), event.actor());
    }

    @Test
    void depositIsGivenBackWhenItCannotBeStored() {
        creditsFail.set(true);

        var result = service.deposit(company, worker, amount("40")).join();

        assertEquals(Outcome.FAILED, result.outcome());
        assertMoney("100", worker);
        assertEquals(0, balance());
        assertTrue(events.isEmpty());
    }

    @Test
    void depositAboveThePlayersMoneyTakesNothing() {
        var result = service.deposit(company, worker, amount("100.01")).join();

        assertEquals(Outcome.PLAYER_FUNDS, result.outcome());
        assertMoney("100", worker);
        assertEquals(0, balance());
    }

    @Test
    void invalidAmountsAreRefused() {
        settings.minTransaction(5.0);

        assertEquals(
                Outcome.INVALID_AMOUNT,
                service.deposit(company, worker, amount("4.99")).join().outcome());
        assertEquals(
                Outcome.INVALID_AMOUNT,
                service.deposit(company, worker, amount("5.001")).join().outcome());
        assertEquals(
                Outcome.INVALID_AMOUNT,
                service.deposit(company, worker, amount("-5")).join().outcome());
        assertEquals(
                Outcome.DONE,
                service.deposit(company, worker, amount("5.000")).join().outcome());
    }

    @Test
    void outsidersAndDisabledTreasuriesAreRefused() {
        assertEquals(
                Outcome.NO_MEMBER,
                service.deposit(company, stranger, amount("5")).join().outcome());

        settings.enabled(false);

        assertEquals(
                Outcome.DISABLED,
                service.deposit(company, worker, amount("5")).join().outcome());
        assertMoney("100", worker);
    }

    @Test
    void withdrawNeedsTheTreasuryPermission() {
        fund(5000);

        assertEquals(
                Outcome.NO_PERMISSION,
                service.withdraw(company, worker, amount("10")).join().outcome());
        assertEquals(
                Outcome.DONE,
                service.withdraw(company, treasurer, amount("10")).join().outcome());
        assertEquals(
                Outcome.DONE,
                service.withdraw(company, owner, amount("10")).join().outcome());

        assertEquals(3000, balance());
        assertMoney("110", treasurer);
        assertMoney("10", owner);
        assertMoney("100", worker);
    }

    @Test
    void withdrawAboveTheBalanceTakesNothing() {
        fund(1000);

        var result = service.withdraw(company, treasurer, amount("10.01")).join();

        assertEquals(Outcome.TREASURY_FUNDS, result.outcome());
        assertEquals(1000, balance());
        assertMoney("100", treasurer);
    }

    @Test
    void withdrawIsCreditedBackWhenVaultRefusesThePayment() {
        fund(5000);
        vaultRefusesDeposits.set(true);

        var result = service.withdraw(company, treasurer, amount("20")).join();

        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals(5000, balance());
        assertMoney("100", treasurer);
        assertEquals(List.of(TreasuryKind.REFUND, TreasuryKind.WITHDRAW, TreasuryKind.ADMIN_GIVE), logKinds());
    }

    @Test
    void concurrentWithdrawalsNeverPayOutMoreThanTheBalance() throws Exception {
        fund(10000);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(10);
        List<CompletableFuture<TreasuryService.Result>> results = new ArrayList<>();
        try {
            for (var i = 0; i < 10; i++) {
                var who = i % 2 == 0 ? treasurer : owner;
                results.add(CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return service.withdraw(company, who, amount("30")).join();
                        },
                        pool));
            }
            start.countDown();
            var done = results.stream()
                    .map(CompletableFuture::join)
                    .filter(TreasuryService.Result::done)
                    .count();

            assertEquals(3, done);
            assertEquals(1000, balance());
            var paidOut = wallet(treasurer).add(wallet(owner)).subtract(amount("100"));
            assertEquals(0, amount("90").compareTo(paidOut));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSpendAndWithdrawCannotUseTheSameMoney() throws Exception {
        fund(5000);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var spend = CompletableFuture.supplyAsync(
                    () -> {
                        await(start);
                        return service.spend(
                                        company,
                                        owner.getUniqueId(),
                                        amount("50"),
                                        TreasuryKind.RENAME,
                                        "Diggers",
                                        TreasuryServiceTest::bought)
                                .join();
                    },
                    pool);
            var withdraw = CompletableFuture.supplyAsync(
                    () -> {
                        await(start);
                        return service.withdraw(company, treasurer, amount("50"))
                                .join();
                    },
                    pool);
            start.countDown();

            assertTrue(spend.join().done() ^ withdraw.join().done());
            assertEquals(0, balance());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void spendPaysOnlyWhatTheBalanceCovers() {
        fund(2500);

        var refused = service.spend(
                        company,
                        owner.getUniqueId(),
                        amount("25.01"),
                        TreasuryKind.RENAME,
                        "Diggers",
                        TreasuryServiceTest::bought)
                .join();
        var paid = service.spend(
                        company,
                        owner.getUniqueId(),
                        amount("25"),
                        TreasuryKind.RENAME,
                        "Diggers",
                        TreasuryServiceTest::bought)
                .join();

        assertEquals(Outcome.TREASURY_FUNDS, refused.outcome());
        assertEquals(Outcome.DONE, paid.outcome());
        assertEquals(0, balance());
        var entry = data.retrieveLog(company.id(), 1, 1).join().get(0);
        assertEquals(TreasuryKind.RENAME, entry.kind());
        assertEquals(-2500, entry.amount());
        assertEquals("Diggers", entry.note());
    }

    @Test
    void refundGivesASpentCostBack() {
        fund(2500);
        service.spend(
                        company,
                        owner.getUniqueId(),
                        amount("25"),
                        TreasuryKind.RENAME,
                        "Diggers",
                        TreasuryServiceTest::bought)
                .join();

        var result = service.refund(company, owner.getUniqueId(), amount("25"), "Diggers")
                .join();

        assertTrue(result.done());
        assertEquals(2500, balance());
        assertEquals(TreasuryKind.REFUND, logKinds().get(0));
    }

    @Test
    void shareIsSetByTreasurersUpToTheCapAndEveryMemberIsNotified() {
        settings.maxPayoutShare(20);

        assertEquals(
                Outcome.NO_PERMISSION,
                service.setShare(company, worker, 10).join().outcome());
        assertEquals(
                Outcome.SHARE_CAP,
                service.setShare(company, treasurer, 21).join().outcome());
        assertTrue(notices.isEmpty());

        var result = service.setShare(company, treasurer, 20).join();

        assertTrue(result.done());
        assertEquals(20, data.retrieveTreasury(company.id()).join().payoutShare());
        assertEquals(3, notices.size());
        assertTrue(notices.stream().allMatch(notice -> notice.type() == NotificationType.TREASURY_SHARE_CHANGED));
        assertEquals(
                company.members().stream().map(member -> member.uuid()).toList(),
                notices.stream().map(Notice::recipient).toList());
        assertEquals("20", notices.get(0).args().values().get("percent").value());
        var event = (TreasuryTransactionEvent) events.get(0);
        assertEquals(TreasuryKind.SHARE_CHANGED, event.kind());
        assertEquals(0, event.amount().signum());
    }

    @Test
    void effectiveShareFollowsTheCurrentCap() {
        settings.maxPayoutShare(30);
        service.setShare(company, owner, 30).join();

        assertEquals(30, service.effectiveShare(company.id()).join());

        settings.maxPayoutShare(10);
        assertEquals(10, service.effectiveShare(company.id()).join());

        settings.enabled(false);
        assertEquals(0, service.effectiveShare(company.id()).join());
    }

    @Test
    void keepShareCreditsThePayoutShareOfAnOrder() {
        var result = service.keepShare(company, 42, 1387).join();

        assertTrue(result.done());
        var entry = data.retrieveLog(company.id(), 1, 1).join().get(0);
        assertEquals(TreasuryKind.PAYOUT_SHARE, entry.kind());
        assertEquals(Optional.of(42), entry.orderId());
        assertEquals(Optional.empty(), entry.actorId());
        assertEquals(1387, entry.balance());
    }

    @Test
    void disbandPaysTheBalanceToTheOwner() {
        fund(12345);

        var drained = service.drain(company, owner.getUniqueId()).join();
        var paid = service.payOwner(company, drained).join();

        assertEquals(12345, drained);
        assertTrue(paid);
        assertEquals(0, balance());
        assertMoney("123.45", owner);
        var notice = notices.get(0);
        assertEquals(owner.getUniqueId(), notice.recipient());
        assertEquals(NotificationType.TREASURY_DISBAND_PAYOUT, notice.type());
        assertEquals("Miners", notice.args().values().get("company_name").value());
        var event = (TreasuryTransactionEvent) events.get(0);
        assertEquals(TreasuryKind.DISBAND, event.kind());
        assertEquals(amount("-123.45"), event.amount());
    }

    @Test
    void disbandingAnEmptyTreasuryPaysNothing() {
        var drained = service.drain(company, owner.getUniqueId()).join();

        assertEquals(0, drained);
        assertFalse(service.payOwner(company, drained).join());
        assertTrue(notices.isEmpty());
        assertMoney("0", owner);
    }

    @Test
    void undoDrainRestoresTheBalance() {
        fund(700);
        var drained = service.drain(company, owner.getUniqueId()).join();

        service.undoDrain(company, drained, owner.getUniqueId()).join();

        assertEquals(700, balance());
    }

    @Test
    void adminTakeCannotOverdraw() {
        fund(500);

        var taken = service.adminAdjust(company, owner.getUniqueId(), false, amount("5.01"))
                .join();

        assertEquals(Outcome.TREASURY_FUNDS, taken.outcome());
        assertEquals(500, balance());
    }

    @Test
    void failedPurchaseGivesTheCostBack() {
        fund(2500);

        var result = service.spend(
                        company,
                        owner.getUniqueId(),
                        amount("25"),
                        TreasuryKind.RENAME,
                        "Diggers",
                        () -> CompletableFuture.completedFuture(false))
                .join();

        assertEquals(Outcome.PURCHASE_FAILED, result.outcome());
        assertEquals(2500, balance());
        assertEquals(List.of(TreasuryKind.REFUND, TreasuryKind.RENAME, TreasuryKind.ADMIN_GIVE), logKinds());
    }

    @Test
    void purchaseDoesNotRunWithoutEnoughMoney() {
        fund(100);
        var ran = new AtomicBoolean();

        var result = service.spend(company, owner.getUniqueId(), amount("25"), TreasuryKind.RENAME, "Diggers", () -> {
                    ran.set(true);
                    return CompletableFuture.completedFuture(true);
                })
                .join();

        assertEquals(Outcome.TREASURY_FUNDS, result.outcome());
        assertFalse(ran.get());
        assertEquals(100, balance());
    }

    private static CompletableFuture<Boolean> bought() {
        return CompletableFuture.completedFuture(true);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Notice(UUID recipient, NotificationType type, NotificationArgs args) {}

    private final class FakeWallet implements TreasuryService.Wallet {
        @Override
        public synchronized boolean withdraw(OfflinePlayer player, double amount) {
            var current = wallets.get(player.getUniqueId());
            var value = BigDecimal.valueOf(amount);
            if (current.compareTo(value) < 0) return false;
            wallets.put(player.getUniqueId(), current.subtract(value));
            return true;
        }

        @Override
        public synchronized boolean deposit(OfflinePlayer player, double amount) {
            if (vaultRefusesDeposits.get()) return false;
            wallets.merge(player.getUniqueId(), BigDecimal.valueOf(amount), BigDecimal::add);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }
}
