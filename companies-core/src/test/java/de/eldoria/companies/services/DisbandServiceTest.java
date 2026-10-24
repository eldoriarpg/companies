/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.companysettings.TreasurySettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisbandServiceTest {
    private final Map<UUID, Double> paid = new ConcurrentHashMap<>();
    private final List<NotificationType> notices = new CopyOnWriteArrayList<>();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private Plugin plugin;
    private SqLiteCompanyData companyData;
    private SqLiteTreasuryData treasuryData;
    private DisbandService disband;
    private UUID owner;
    private UUID admin;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        treasuryData = new SqLiteTreasuryData(executor);
        var orders = OrderTable.create();
        var notifications = new NotificationService(
                plugin,
                new SqLiteInboxData(executor),
                orders.orderData(),
                null,
                NotificationSettings::new,
                (player, type, args) -> {},
                Runnable::run);
        var treasury = new TreasuryService(
                treasuryData,
                new PayingWallet(),
                TreasurySettings::new,
                (recipient, type, args) -> notices.add(type),
                event -> {},
                Runnable::run,
                Clock.systemDefaultZone(),
                Logger.getLogger("DisbandServiceTest"));
        disband = new DisbandService(
                plugin,
                companyData,
                orders.orderData(),
                new ItemReturnService(plugin, orders.orderData()),
                notifications,
                treasury);
        owner = UUID.randomUUID();
        admin = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private CompanyProfile company(long treasuryCents) {
        var id = companyData.submitCompanyCreation("Acme", JoinMode.INVITE_ONLY).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(id, owner, CompanyPermission.OWNER.mask()))
                .join();
        if (treasuryCents > 0) {
            treasuryData
                    .submitCredit(
                            id, treasuryCents, new TreasuryEntry(TreasuryKind.DEPOSIT, owner, null, null, Times.now()))
                    .join();
        }
        return companyData
                .retrieveCompanyProfile(
                        companyData.retrieveCompanyById(id).join().orElseThrow())
                .join()
                .orElseThrow();
    }

    @Test
    void disbandPaysTheTreasuryToTheOwner() {
        var company = company(4250);

        assertTrue(disband.disband(company));

        assertEquals(Map.of(owner, 42.5), paid);
        assertEquals(List.of(NotificationType.TREASURY_DISBAND_PAYOUT), notices);
        assertEquals(0, SqLiteTestDatabase.count("company_treasury", "TRUE"));
        assertEquals(0, SqLiteTestDatabase.count("treasury_log", "TRUE"));
    }

    @Test
    void adminDeletePaysTheTreasuryToTheOwnerNotTheAdmin() {
        var company = company(100);

        assertTrue(disband.disband(company, admin));

        assertEquals(Map.of(owner, 1.0), paid);
    }

    @Test
    void emptyTreasuryPaysNothing() {
        assertTrue(disband.disband(company(0)));

        assertTrue(paid.isEmpty());
        assertTrue(notices.isEmpty());
    }

    private final class PayingWallet implements TreasuryService.Wallet {
        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            paid.merge(player.getUniqueId(), amount, Double::sum);
            return true;
        }

        @Override
        public String format(double amount) {
            return BigDecimal.valueOf(amount).toPlainString();
        }
    }
}
