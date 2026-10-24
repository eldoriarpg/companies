/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companysettings.TreasurySettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.company.PreCompanyCreateEvent;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.services.CompanyService.Payment;
import de.eldoria.companies.util.TestExecutors;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CompanyServiceTest {
    private static final double FOUNDING = 500.0;
    private static final double RENAME = 100.0;

    @TempDir
    Path directory;

    private final Map<UUID, Double> wallets = new ConcurrentHashMap<>();
    private final Map<UUID, Player> online = new ConcurrentHashMap<>();
    private final CompanySettings settings = new CompanySettings();
    private final TreasurySettings treasurySettings = new TreasurySettings();
    private final EventRecorder events = new EventRecorder();
    private Consumer<PreCompanyCreateEvent> listener = event -> {};

    private ServerMock server;
    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private SqLiteTreasuryData treasuryData;
    private TreasuryService treasury;
    private CompanyService service;

    private OfflinePlayer owner;
    private OfflinePlayer worker;
    private int miners;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        treasuryData = new SqLiteTreasuryData(executor);
        settings.foundingPrice(FOUNDING);
        settings.renamePrice(RENAME);
        treasury = new TreasuryService(
                treasuryData,
                new TreasuryWallet(),
                () -> treasurySettings,
                (recipient, type, args) -> {},
                event -> {},
                Runnable::run,
                Clock.systemDefaultZone(),
                Logger.getLogger("test"));
        Consumer<Event> fired = event -> {
            events.accept(event);
            if (event instanceof PreCompanyCreateEvent pre) listener.accept(pre);
        };
        service = new CompanyService(
                companyData,
                new CompanyChanges(companyData, server::getOfflinePlayer, fired, Logger.getLogger("test"), executor),
                treasury,
                new Wallet(),
                () -> settings,
                server::getOfflinePlayer,
                uuid -> Optional.ofNullable(online.get(uuid)),
                Runnable::run,
                executor,
                Logger.getLogger("test"));

        owner = player(0);
        worker = player(0);
        miners = companyData
                .submitCompanyCreation("Miners", JoinMode.INVITE_ONLY)
                .join();
        member(owner, CompanyPermission.OWNER);
        member(worker, CompanyPermission.MANAGE_ORDERS);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private OfflinePlayer player(double money) {
        var player = server.getOfflinePlayer(UUID.randomUUID());
        wallets.put(player.getUniqueId(), money);
        return player;
    }

    private Player onlinePlayer(double money) {
        var player = server.addPlayer();
        wallets.put(player.getUniqueId(), money);
        online.put(player.getUniqueId(), player);
        return player;
    }

    private void member(OfflinePlayer player, CompanyPermission... permissions) {
        companyData
                .submitMemberUpdate(CompanyMember.of(
                        miners, player.getUniqueId(), CompanyPermission.composePermissions(permissions)))
                .join();
    }

    private double wallet(OfflinePlayer player) {
        return wallets.get(player.getUniqueId());
    }

    private Optional<CompanyProfile> companyOf(OfflinePlayer player) {
        return companyData.retrievePlayerCompanyProfile(player).join();
    }

    private String nameOfMiners() {
        return companyData.retrieveCompanyById(miners).join().orElseThrow().name();
    }

    private long treasuryBalance() {
        return treasuryData.retrieveTreasury(miners).join().cents();
    }

    private static <T> T done(ActionResult<T> result) {
        if (result instanceof ActionResult.Done<T>(var value)) return value;
        return fail("refused with " + refusalKey(result));
    }

    private static String refusalKey(ActionResult<?> result) {
        return result instanceof ActionResult.Refused<?> refused
                ? refused.failure().localeKey()
                : "done";
    }

    private static void assertRefused(String key, ActionResult<?> result) {
        assertEquals(key, refusalKey(result));
    }

    @Test
    void foundingChargesThePriceAndMakesTheFounderOwner() {
        var founder = player(FOUNDING + 10);

        var id = done(service.found(founder.getUniqueId(), "Smiths").join());

        var company = companyOf(founder).orElseThrow();
        assertEquals(id, company.id());
        assertEquals("Smiths", company.name());
        assertTrue(company.member(founder).orElseThrow().isOwner());
        assertEquals(10.0, wallet(founder));
    }

    @Test
    void foundingsAtOnceChargeOnceAndFoundOne() {
        var founder = player(3 * FOUNDING + 10);

        var first = service.found(founder.getUniqueId(), "Smiths");
        var second = service.found(founder.getUniqueId(), "Masons");
        var third = service.found(founder.getUniqueId(), "Carvers");
        var results = List.of(first.join(), second.join(), third.join());

        assertEquals(
                1, results.stream().filter(ActionResult.Done.class::isInstance).count());
        assertEquals(
                List.of("error.alreadyMember", "error.alreadyMember"),
                results.stream()
                        .filter(ActionResult.Refused.class::isInstance)
                        .map(CompanyServiceTest::refusalKey)
                        .toList());
        assertEquals(2 * FOUNDING + 10, wallet(founder));
    }

    @Test
    void foundingIsRefusedForMembersTakenNamesAndPoorFounders() {
        assertRefused(
                "error.alreadyMember",
                service.found(worker.getUniqueId(), "Smiths").join());
        var founder = player(FOUNDING - 1);
        assertRefused(
                "error.companyNameUsed",
                service.found(founder.getUniqueId(), "Miners").join());
        assertRefused(
                "error.insufficientCurrency",
                service.found(founder.getUniqueId(), "Smiths").join());
        assertRefused(
                "error.invalidLength",
                service.found(founder.getUniqueId(), "x".repeat(CompanyService.MAX_NAME_LENGTH))
                        .join());

        assertTrue(companyOf(founder).isEmpty());
        assertEquals(FOUNDING - 1, wallet(founder));
    }

    @Test
    void anOnlineFounderAsksTheIntegrationsFirst() {
        var founder = onlinePlayer(FOUNDING);
        listener = event -> event.cancel(Component.text("No"));

        assertRefused(
                "error.cancelledByIntegration",
                service.found(founder.getUniqueId(), "Smiths").join());

        assertSame(founder, events.single(PreCompanyCreateEvent.class).player());
        assertTrue(companyOf(founder).isEmpty());
        assertEquals(FOUNDING, wallet(founder));
    }

    @Test
    void anOfflineFounderSkipsThePlayerTypedPreEvent() {
        var founder = player(FOUNDING);
        listener = event -> event.setCancelled(true);

        done(service.found(founder.getUniqueId(), "Smiths").join());

        assertTrue(events.of(PreCompanyCreateEvent.class).isEmpty());
        assertTrue(companyOf(founder).isPresent());
    }

    @Test
    void foundableChecksWithoutCharging() {
        var founder = player(FOUNDING);

        assertEquals(
                "Smiths",
                done(service.foundable(founder.getUniqueId(), "Smiths").join()));

        assertTrue(companyOf(founder).isEmpty());
        assertEquals(FOUNDING, wallet(founder));
    }

    @Test
    void onlyTheOwnerRenames() {
        wallets.put(worker.getUniqueId(), RENAME);

        assertRefused(
                "error.noOwner",
                service.rename(worker.getUniqueId(), "Diggers", Payment.PLAYER).join());
        assertRefused(
                "error.noMember",
                service.rename(player(RENAME).getUniqueId(), "Diggers", Payment.PLAYER)
                        .join());
        assertEquals("Miners", nameOfMiners());
        assertEquals(RENAME, wallet(worker));
    }

    @Test
    void theOwnerPaysTheRename() {
        wallets.put(owner.getUniqueId(), RENAME + 5);

        done(service.rename(owner.getUniqueId(), "Diggers", Payment.PLAYER).join());

        assertEquals("Diggers", nameOfMiners());
        assertEquals(5.0, wallet(owner));
    }

    @Test
    void aRenameTheOwnerCanNotAffordChangesNothing() {
        wallets.put(owner.getUniqueId(), RENAME - 1);

        assertRefused(
                "error.insufficientCurrency",
                service.rename(owner.getUniqueId(), "Diggers", Payment.PLAYER).join());

        assertEquals("Miners", nameOfMiners());
        assertEquals(RENAME - 1, wallet(owner));
    }

    @Test
    void theTreasuryPaysTheRename() {
        wallets.put(owner.getUniqueId(), 150.0);
        treasury.deposit(companyOf(owner).orElseThrow(), owner, BigDecimal.valueOf(150))
                .join();

        var quote = done(service.renamable(owner.getUniqueId(), "Diggers").join());
        assertEquals(BigDecimal.valueOf(15000, 2), quote.treasuryBalance().orElseThrow());

        done(service.rename(owner.getUniqueId(), "Diggers", Payment.TREASURY).join());

        assertEquals("Diggers", nameOfMiners());
        assertEquals(5000, treasuryBalance());
        assertEquals(0.0, wallet(owner));
    }

    @Test
    void aTreasuryThatCanNotPayKeepsTheName() {
        assertRefused(
                "company.bank.error.insufficient",
                service.rename(owner.getUniqueId(), "Diggers", Payment.TREASURY).join());
        treasurySettings.payRename(false);
        assertRefused(
                "company.bank.error.disabled",
                service.rename(owner.getUniqueId(), "Diggers", Payment.TREASURY).join());
        assertTrue(done(service.renamable(owner.getUniqueId(), "Diggers").join())
                .treasuryBalance()
                .isEmpty());

        assertEquals("Miners", nameOfMiners());
    }

    @Test
    void aTakenNameIsRefusedBeforePaying() {
        companyData.submitCompanyCreation("Diggers", JoinMode.INVITE_ONLY).join();
        wallets.put(owner.getUniqueId(), RENAME);

        assertRefused(
                "error.companyNameUsed",
                service.rename(owner.getUniqueId(), "Diggers", Payment.PLAYER).join());

        assertEquals(RENAME, wallet(owner));
        assertFalse(nameOfMiners().equals("Diggers"));
    }

    private final class Wallet implements OrderCreationService.Wallet {
        @Override
        public synchronized boolean withdraw(OfflinePlayer player, double amount) {
            var current = wallets.getOrDefault(player.getUniqueId(), 0.0);
            if (current < amount) return false;
            wallets.put(player.getUniqueId(), current - amount);
            return true;
        }

        @Override
        public synchronized void deposit(OfflinePlayer player, double amount) {
            wallets.merge(player.getUniqueId(), amount, Double::sum);
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }

        @Override
        public String currencyName() {
            return "coins";
        }
    }

    private final class TreasuryWallet implements TreasuryService.Wallet {
        private final Wallet wallet = new Wallet();

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return wallet.withdraw(player, amount);
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            wallet.deposit(player, amount);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }
}
