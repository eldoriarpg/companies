/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CancelOrderResult;
import de.eldoria.companies.api.write.DisbandResult;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.companysettings.TreasurySettings;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAreaData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHomeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLitePerkData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLitePriceData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerWritesApiTest {
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private Plugin plugin;
    private SqLiteCompanyData companyData;
    private SqLiteTreasuryData treasuryData;
    private OrderTable orders;
    private final EventRecorder removals = new EventRecorder();
    private EventRecorder disbands;
    private volatile boolean failPurge;
    private CompaniesApiImpl api;
    private UUID owner;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor) {
            @Override
            public boolean purgeCompany(SimpleCompany company) {
                return !failPurge && super.purgeCompany(company);
            }
        };
        disbands = EventRecorder.listen(plugin, CompanyDisbandEvent.class);
        treasuryData = new SqLiteTreasuryData(executor);
        orders = OrderTable.create();
        owner = orders.owner().getUniqueId();
        var orderData = orders.orderData();
        var itemReturns = new ItemReturnService(plugin, orderData);
        var notifications = new NotificationService(
                plugin,
                new SqLiteInboxData(executor),
                orderData,
                null,
                NotificationSettings::new,
                (player, type, args) -> {},
                Runnable::run);
        var treasury = new TreasuryService(
                treasuryData,
                new TreasuryWallet(),
                TreasurySettings::new,
                (recipient, type, args) -> {},
                event -> {},
                Runnable::run,
                Clock.systemDefaultZone(),
                Logger.getAnonymousLogger());
        var disband =
                new DisbandService(plugin, companyData, orderData, itemReturns, notifications, treasury, company -> 0);
        var outcomes = new OrderOutcomes(
                orderData,
                new HistoryRecorder(
                        new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor), Clock.systemDefaultZone()),
                itemReturns,
                removals,
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        var ownerOrders = new OwnerOrderService(
                orderData,
                outcomes,
                id -> CompletableFuture.completedFuture(Optional.empty()),
                order -> CompletableFuture.completedFuture(false),
                OrderSettings::new,
                new OrderWallet(),
                amount -> {},
                changed -> {},
                SqLiteTestDatabase::player,
                Runnable::run,
                executor,
                Logger.getAnonymousLogger());
        api = new CompaniesApiImpl(
                new CompaniesApiImpl.Repositories(
                        companyData,
                        orderData,
                        new SqLiteStatsData(executor),
                        treasuryData,
                        new SqLiteServerEventData(),
                        new SqLiteAreaData(),
                        new SqLiteHomeData(),
                        new SqLiteJobData(),
                        new SqLitePriceData(),
                        new SqLitePerkData()),
                new CompaniesApiImpl.Server(
                        server::getOfflinePlayer,
                        failure -> Component.text(failure.localeKey()),
                        LocalDateTime::now,
                        event -> {}),
                executor);
        api.open(new CompaniesApiImpl.Writes(
                null,
                null,
                itemReturns,
                null,
                null,
                null,
                null,
                null,
                JobSettings::new,
                disband,
                ownerOrders,
                Runnable::run));
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private int company(UUID companyOwner, long treasuryCents) {
        var id = companyData.submitCompanyCreation("Acme", JoinMode.OPEN).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(id, companyOwner, CompanyPermission.OWNER.mask()))
                .join();
        treasuryData
                .submitCredit(
                        id,
                        treasuryCents,
                        new TreasuryEntry(TreasuryKind.DEPOSIT, companyOwner, null, null, Times.now()))
                .join();
        return id;
    }

    @Test
    void theOwnerDisbandsTheCompanyAndGetsTheTreasury() throws Exception {
        var acme = company(owner, 2500);

        assertEquals(
                DisbandResult.DISBANDED, api.companies().disband(acme, owner).get(5, TimeUnit.SECONDS));

        assertTrue(api.companies().byId(acme).get(5, TimeUnit.SECONDS).isEmpty());
        assertEquals(25.0, balances.get(owner));
        assertEquals(acme, disbands.single(CompanyDisbandEvent.class).company().id());
    }

    @Test
    void onlyTheOwnerDisbands() throws Exception {
        var member = UUID.randomUUID();
        var acme = company(owner, 0);
        companyData.submitMemberUpdate(CompanyMember.of(acme, member, 0)).join();

        assertEquals(
                DisbandResult.NOT_OWNER, api.companies().disband(acme, member).get(5, TimeUnit.SECONDS));
        assertEquals(
                DisbandResult.NOT_OWNER,
                api.companies().disband(acme, UUID.randomUUID()).get(5, TimeUnit.SECONDS));
        assertEquals(
                DisbandResult.NOT_OWNER, api.companies().disband(999, owner).get(5, TimeUnit.SECONDS));
        assertTrue(api.companies().byId(acme).get(5, TimeUnit.SECONDS).isPresent());
    }

    @Test
    void theOwnerCancelsAnUnclaimedOrderAndGetsThePriceBack() throws Exception {
        var order = orders.place(5.0);

        assertEquals(
                CancelOrderResult.CANCELLED,
                api.orders().cancel(order.id(), owner).get(5, TimeUnit.SECONDS));

        assertTrue(api.orders().byId(order.id()).get(5, TimeUnit.SECONDS).isEmpty());
        assertEquals(150.0, balances.get(owner));
        assertEquals(
                OrderRemovedEvent.Reason.CANCELLED,
                removals.single(OrderRemovedEvent.class).reason());
    }

    @Test
    void cancellingNamesWhyAnOrderStays() throws Exception {
        var claimed = orders.place(0.0);
        orders.state(claimed, OrderState.CLAIMED, 1);
        var open = orders.place(0.0);

        assertEquals(
                CancelOrderResult.CLAIMED,
                api.orders().cancel(claimed.id(), owner).get(5, TimeUnit.SECONDS));
        assertEquals(
                CancelOrderResult.NOT_OWNER,
                api.orders().cancel(open.id(), UUID.randomUUID()).get(5, TimeUnit.SECONDS));
        assertEquals(CancelOrderResult.UNKNOWN, api.orders().cancel(999, owner).get(5, TimeUnit.SECONDS));
        assertTrue(api.orders().byId(claimed.id()).get(5, TimeUnit.SECONDS).isPresent());
        assertTrue(api.orders().byId(open.id()).get(5, TimeUnit.SECONDS).isPresent());
        assertFalse(balances.containsKey(owner));
    }

    @Test
    void aDisbandTheDatabaseRefusesFailsAndKeepsTheTreasury() throws Exception {
        var acme = company(owner, 2500);
        failPurge = true;

        assertEquals(DisbandResult.FAILED, api.companies().disband(acme, owner).get(5, TimeUnit.SECONDS));

        assertTrue(api.companies().byId(acme).get(5, TimeUnit.SECONDS).isPresent());
        var treasury = api.companies().treasury(acme).get(5, TimeUnit.SECONDS).orElseThrow();
        assertEquals(0, new BigDecimal("25").compareTo(treasury.balance()));
        assertFalse(balances.containsKey(owner));
        assertTrue(disbands.of(CompanyDisbandEvent.class).isEmpty());
    }

    @Test
    void anUnchargedOrderIsCancelledWithoutARefund() throws Exception {
        var order = orders.place(0.0, false);

        assertEquals(
                CancelOrderResult.CANCELLED,
                api.orders().cancel(order.id(), owner).get(5, TimeUnit.SECONDS));

        assertFalse(balances.containsKey(owner));
    }

    @Test
    void theServerOwnerIdIsRefused() {
        var err = assertThrows(
                ExecutionException.class,
                () -> api.orders().cancel(1, SystemOwner.ID).get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, err.getCause());
    }

    private final class TreasuryWallet implements TreasuryService.Wallet {
        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            balances.merge(player.getUniqueId(), amount, Double::sum);
            return true;
        }

        @Override
        public String format(double amount) {
            return BigDecimal.valueOf(amount).toPlainString();
        }
    }

    private final class OrderWallet implements OrderCreationService.Wallet {
        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public void deposit(OfflinePlayer player, double amount) {
            balances.merge(player.getUniqueId(), amount, Double::sum);
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
}
