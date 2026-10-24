/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CompanyOrderServiceTest {
    @TempDir
    Path directory;

    private final EventRecorder events = new EventRecorder();
    private final List<UUID> changed = new CopyOnWriteArrayList<>();
    private final UUID manager = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    private ExecutorService executor;
    private OrderTable orders;
    private CompanyOrderService service;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        var companyData = new SqLiteCompanyData(executor);
        company = companyData
                .submitCompanyCreation("Miners", JoinMode.INVITE_ONLY)
                .join();
        companyData
                .submitMemberUpdate(CompanyMember.of(company, manager, CompanyPermission.MANAGE_ORDERS.mask()))
                .join();
        companyData.submitMemberUpdate(CompanyMember.of(company, worker, 0)).join();
        var history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        var outcomes = new OrderOutcomes(
                orders.orderData(),
                new HistoryRecorder(history, Clock.systemDefaultZone()),
                new ItemReturnService(plugin, orders.orderData()),
                new EventRecorder(),
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        service = new CompanyOrderService(
                companyData, orders.orderData(), outcomes, events, changed::add, SqLiteTestDatabase::player, executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private SimpleOrder claimed() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, company);
        return order;
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
    void abortingOpensTheOrderAgainAndAnnouncesIt() {
        var order = claimed();

        var aborted = done(service.abort(manager, order.id()).join());

        assertEquals(company, aborted.company().id());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        var event = events.single(OrderCanceledEvent.class);
        assertEquals(order.id(), event.order().id());
        assertEquals(company, event.company().id());
        assertEquals(List.of(manager), changed);
    }

    @Test
    void abortingNeedsManageOrders() {
        var order = claimed();

        assertRefused(
                "error.permission.cancelOrder",
                service.abort(worker, order.id()).join());
        assertRefused("error.noMember", service.abort(outsider, order.id()).join());

        assertEquals(OrderState.CLAIMED, orders.reload(order).state());
        assertTrue(events.of(OrderCanceledEvent.class).isEmpty());
    }

    @Test
    void onlyOrdersTheCompanyWorksOnAreAborted() {
        var open = orders.place(0.0);
        orders.state(open, OrderState.UNCLAIMED, company);
        var other = orders.place(0.0);
        orders.state(other, OrderState.CLAIMED, company + 1);

        assertRefused("error.orderNotActive", service.abort(manager, open.id()).join());
        assertRefused("error.orderNotOwned", service.abort(manager, other.id()).join());
        assertRefused(
                "error.unknownOrder", service.abort(manager, other.id() + 100).join());

        assertEquals(OrderState.CLAIMED, orders.reload(other).state());
        assertTrue(changed.isEmpty());
    }

    @Test
    void abortableChecksWithoutAborting() {
        var order = claimed();

        var aborted = done(service.abortable(manager, order.id()).join());

        assertEquals(order.id(), aborted.order().id());
        assertEquals(OrderState.CLAIMED, orders.reload(order).state());
    }
}
