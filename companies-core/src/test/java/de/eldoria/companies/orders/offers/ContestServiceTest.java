/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.offer.ContestDelivery;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.orders.DeliveryPart;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContestServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T08:00:00Z");

    @TempDir
    Path directory;

    private final UUID manager = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();
    private final Map<UUID, Double> paid = new HashMap<>();
    private final List<Double> collected = new ArrayList<>();
    private final List<Long> kept = new ArrayList<>();
    private ExecutorService executor;
    private SqLiteOfferData offers;
    private SqLiteOrderData orders;
    private SqLiteCompanyData companies;
    private ContestService service;
    private CompanyProfile company;
    private int slots = 2;
    private boolean failPayment;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
        orders = OrderTable.create().orderData();
        companies = new SqLiteCompanyData(executor);
        var id = companies.submitCompanyCreation("Miners", JoinMode.INVITE_ONLY).join();
        companies
                .submitMemberUpdate(CompanyMember.of(id, manager, CompanyPermission.MANAGE_ORDERS.mask()))
                .join();
        companies.submitMemberUpdate(CompanyMember.of(id, worker, 0)).join();
        var simple = companies.retrieveCompanyById(id).join().orElseThrow();
        company = companies.retrieveCompanyProfile(simple).join().orElseThrow();
        var payments = new ContestService.Payments() {
            @Override
            public double taxPercent(CompanyProfile company) {
                return 10;
            }

            @Override
            public int share(CompanyProfile company) {
                return 20;
            }

            @Override
            public void pay(UUID worker, double amount) {
                if (failPayment) throw new IllegalStateException("the economy refused");
                paid.merge(worker, amount, Double::sum);
            }

            @Override
            public void collect(double amount) {
                collected.add(amount);
            }

            @Override
            public void keep(CompanyProfile company, int offer, long cents) {
                kept.add(cents);
            }
        };
        service = new ContestService(
                offers,
                orders,
                payments,
                found -> slots,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void onlyMembersWhoManageOrdersJoinAndLeave() {
        var id = contest(100, 2.0, List.of(), 200.0, true);

        assertEquals(ContestService.Joining.NO_PERMISSION, service.join(company, worker, id));
        assertEquals(ContestService.Joining.JOINED, service.join(company, manager, id));
        assertEquals(ContestService.Joining.ALREADY, service.join(company, manager, id));
        assertEquals(ContestService.Joining.NO_PERMISSION, service.leave(company, worker, id));
        assertEquals(ContestService.Joining.LEFT, service.leave(company, manager, id));
        assertEquals(ContestService.Joining.NOT_JOINED, service.leave(company, manager, id));
        assertEquals(ContestService.Joining.UNKNOWN, service.join(company, manager, id + 100));
    }

    @Test
    void aContestUsesAnOrderSlotOfTheCompany() {
        slots = 1;
        var first = contest(100, 2.0, List.of(), 200.0, true);
        var second = contest(100, 2.0, List.of(), 200.0, true);

        assertEquals(ContestService.Joining.JOINED, service.join(company, manager, first));
        assertEquals(ContestService.Joining.LIMIT, service.join(company, manager, second));
        service.leave(company, manager, first);
        assertEquals(ContestService.Joining.JOINED, service.join(company, manager, second));
    }

    @Test
    void aClosedContestCannotBeJoined() {
        var id = contest(100, 2.0, List.of(), 200.0, true);
        offers.state(id, OfferState.ACTIVE, OfferState.CANCELLED, LocalDateTime.now());

        assertEquals(ContestService.Joining.CLOSED, service.join(company, manager, id));
    }

    @Test
    void aPaymentThatFailsAfterTheDeliveryKeepsTheDelivery() {
        var id = contest(100, 2.0, List.of(), 200.0, true);
        service.join(company, manager, id);
        failPayment = true;

        var result = service.deliver(company, worker, id, 30);

        assertEquals(ContestDelivery.Outcome.DELIVERED, result.delivery().outcome());
        assertEquals(30, result.delivery().items());
        assertTrue(ContestService.surplus(List.of(new DeliveryPart(0, new ItemStack(Material.OAK_LOG), 30)), 30)
                .isEmpty());
        assertEquals(30, offers.entry(id, company.id()).orElseThrow().delivered());
        assertEquals(30, offers.offer(id).orElseThrow().delivered());
    }

    @Test
    void joiningOpensWhenTheContestStarts() {
        slots = 1;
        var early = contest(100, 2.0, List.of(), 200.0, false);
        var running = contest(100, 2.0, List.of(), 200.0, true);

        assertEquals(ContestService.Joining.NOT_STARTED, service.join(company, manager, early));
        assertEquals(ContestService.Joining.JOINED, service.join(company, manager, running));
        assertEquals(1, orders.retrieveCompanySlotCount(company).join());
        assertEquals(0, orders.retrieveCompanyOrderCount(company).join());
        offers.activate(early, 0, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertEquals(ContestService.Joining.LIMIT, service.join(company, manager, early));
    }

    @Test
    void aDeliveryIsPaidAtOnceWithTaxAndTreasuryShare() {
        var id = contest(100, 2.0, List.of(50.0), 250.0, true);
        service.join(company, manager, id);

        var result = service.deliver(company, worker, id, 30);

        assertEquals(ContestDelivery.Outcome.DELIVERED, result.delivery().outcome());
        assertEquals(30, result.delivery().items());
        var payout = result.payout();
        assertNotNull(payout);
        assertEquals(60.0, payout.gross());
        assertEquals(6.0, payout.tax(), 0.001);
        assertEquals(10.8, payout.share(), 0.001);
        assertEquals(43.2, paid.get(worker), 0.001);
        assertEquals(List.of(6.0), collected);
        assertEquals(List.of(1080L), kept);
        assertEquals(30, offers.entry(id, company.id()).orElseThrow().delivered());
    }

    @Test
    void aDeliveryNeverPaysThePrizesAway() {
        var id = contest(100, 2.0, List.of(150.0), 250.0, true);
        service.join(company, manager, id);

        var result = service.deliver(company, worker, id, 100);

        assertEquals(50, result.delivery().items());
        assertEquals(OfferState.DEPLETED, result.delivery().state());
        assertEquals(
                ContestDelivery.Outcome.NOT_ACTIVE,
                service.deliver(company, worker, id, 1).delivery().outcome());
    }

    @Test
    void aCompanyThatDidNotJoinDeliversNothingAndIsNotPaid() {
        var id = contest(100, 2.0, List.of(), 200.0, true);

        var result = service.deliver(company, worker, id, 10);

        assertEquals(ContestDelivery.Outcome.NOT_JOINED, result.delivery().outcome());
        assertNull(result.payout());
        assertTrue(paid.isEmpty());
    }

    @Test
    void theItemsAContestDidNotTakeGoBack() {
        var oak = new ItemStack(Material.OAK_LOG);
        var birch = new ItemStack(Material.BIRCH_LOG);
        var parts = List.of(new DeliveryPart(0, oak, 40), new DeliveryPart(0, birch, 30));

        var left = ContestService.surplus(parts, 50);

        assertEquals(1, left.size());
        assertEquals(Material.BIRCH_LOG, left.getFirst().stack().getType());
        assertEquals(20, left.getFirst().amount());
        assertEquals(2, ContestService.surplus(parts, 0).size());
        assertTrue(ContestService.surplus(parts, 70).isEmpty());
    }

    @Test
    void aContestWantsItsMaterialItemOrCriteria() {
        var material =
                ContestService.wanted(offer(OfferLine.material("OAK_LOG"))).orElseThrow();
        assertEquals(Material.OAK_LOG, material.template().getType());
        assertNull(material.criteria());

        var criteria = ContestService.wanted(offer(OfferLine.criteria("#logs", RuleEntry.Resolve.ANY)))
                .orElseThrow();
        assertNotNull(criteria.criteria());
        assertTrue(criteria.criteria().accepts(Material.BIRCH_LOG));

        var item = ContestService.wanted(offer(OfferLine.item(new ItemStack(Material.DIAMOND_SWORD), "Sword")))
                .orElseThrow();
        assertEquals(Material.DIAMOND_SWORD, item.template().getType());
        assertTrue(
                ContestService.wanted(offer(OfferLine.material("NO_SUCH_ITEM"))).isEmpty());
    }

    private int contest(int amount, double unit, List<Double> prizes, double budget, boolean active) {
        var now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        var id = offers.create(EventOffer.contest(
                        "Race",
                        OfferLine.material("OAK_LOG"),
                        amount,
                        unit,
                        prizes,
                        SourceType.MINTED,
                        null,
                        budget,
                        now,
                        now.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
        if (active) offers.activate(id, 0, now);
        return id;
    }

    private static EventOffer offer(OfferLine line) {
        var now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return EventOffer.contest(
                "Race", line, 10, 1, List.of(), SourceType.MINTED, null, 10, now, now.plusDays(1), "Admin", now);
    }
}
