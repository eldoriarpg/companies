/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static de.eldoria.companies.menus.order.OrderFixtures.CLAIMED;
import static de.eldoria.companies.menus.order.OrderFixtures.COMPANY;
import static de.eldoria.companies.menus.order.OrderFixtures.OWNER;
import static de.eldoria.companies.menus.order.OrderFixtures.WORKER;
import static de.eldoria.companies.menus.order.OrderFixtures.economy;
import static de.eldoria.companies.menus.order.OrderFixtures.named;
import static de.eldoria.companies.menus.order.OrderFixtures.plain;
import static de.eldoria.companies.menus.order.OrderFixtures.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminMenusTest {
    @BeforeEach
    void useServerZone() {
        Times.useZone(() -> ZoneOffset.UTC);
    }

    @AfterEach
    void useMachineZone() {
        Times.useZone(Times::machineZone);
    }

    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock viewer;
    private MenuActions buttons;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        viewer = server.addPlayer("Admin");
        buttons = new MenuActions(plugin, viewer, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static FullOrder order(OrderState state) {
        var logs = new OrderContent(0, named(Material.OAK_LOG), false, 10, 15);
        logs.deliveries(List.of(new DeliveredStack(1, WORKER, named(Material.OAK_LOG), 3)));
        return new FullOrder(7, OWNER, "<red>Wood", CLAIMED, COMPANY, CLAIMED, state, 2.5, 1, 0, List.of(logs));
    }

    private String plainText(MenuText text) {
        return plain(render(messages, viewer, text));
    }

    private AdminOrderMenu.Model model(OrderState state) {
        var history = new HistoryEntry(
                3,
                7,
                null,
                COMPANY,
                HistoryOutcome.ABORTED,
                "Wood",
                CLAIMED,
                CLAIMED.plusHours(2),
                new HistorySnapshot(List.of(), 0, 0, 0, 0, 0, List.of()));
        return new AdminOrderMenu.Model(
                order(state),
                "Owner",
                state == OrderState.UNCLAIMED ? Optional.empty() : Optional.of("Iron Works"),
                Optional.of(new AdminOrderMenu.Deadline(LocalDateTime.of(2026, 1, 2, 12, 0), false)),
                Map.of(WORKER, "Worker"),
                List.of(history),
                Optional.empty());
    }

    @Test
    void orderShowsBothSidesWorkersMoneyAndHistory() {
        var menu = new AdminOrderMenu(messages, economy(), new NoActions());

        var text = menu.chat(viewer, model(OrderState.CLAIMED), buttons);
        var shown = plainText(text);

        assertTrue(shown.contains("Owner"));
        assertTrue(shown.contains("Iron Works"));
        assertTrue(shown.contains("Worker: 3"));
        assertTrue(shown.contains("$2.50"));
        assertTrue(shown.contains("02.01.26 12:00"));
        assertTrue(shown.contains("enums.historyOutcome.aborted"));
        assertFalse(text.build().contains("run_command"));
    }

    @Test
    void soloOrderNamesTheWorkerInsteadOfACompany() {
        var menu = new AdminOrderMenu(messages, economy(), new NoActions());
        var logs = new OrderContent(0, named(Material.OAK_LOG), false, 10, 15);
        var order = new FullOrder(
                7, OWNER, "Wood", CLAIMED, 0, CLAIMED, OrderState.CLAIMED, 2.5, 1, 0, 0, WORKER, List.of(logs));
        var model = new AdminOrderMenu.Model(
                order,
                "Owner",
                Optional.empty(),
                Optional.empty(),
                Map.of(WORKER, "Loner"),
                List.of(),
                Optional.empty());

        var shown = plainText(menu.chat(viewer, model, buttons));

        assertTrue(shown.contains("companyadmin.order.soloWorker: Loner"), shown);
        assertFalse(shown.contains("companyadmin.order.company:"), shown);
    }

    @Test
    void soloRowsAreMarked() {
        var order = new FullOrder(
                7, OWNER, "Wood", CLAIMED, 0, CLAIMED, OrderState.CLAIMED, 2.5, 1, 0, 0, WORKER, List.of());

        var shown = plainText(
                AdminOrderListMenu.summary("row", new AdminOrderListMenu.Row(order, "Owner", Optional.of("Loner"))));

        assertTrue(shown.contains("Loner (companyadmin.order.solo)"), shown);
    }

    @Test
    void claimedOrderOffersEveryAction() {
        var menu = new AdminOrderMenu(messages, economy(), new NoActions());

        var source = menu.chat(viewer, model(OrderState.CLAIMED), buttons).build();

        assertTrue(source.contains("<act_unclaim>"));
        assertTrue(source.contains("<act_deliver>"));
        assertTrue(source.contains("<act_refund>"));
        assertTrue(source.contains("<act_keep>"));
        assertTrue(source.contains("<act_extend>"));
    }

    @Test
    void openOrderCanOnlyBeCancelledOrExtended() {
        var menu = new AdminOrderMenu(messages, economy(), new NoActions());

        var text = menu.chat(viewer, model(OrderState.UNCLAIMED), buttons);
        var source = text.build();

        assertFalse(source.contains("<act_unclaim>"));
        assertFalse(source.contains("<act_deliver>"));
        assertTrue(source.contains("<act_refund>"));
        assertTrue(source.contains("<act_extend>"));
        assertTrue(plainText(text).contains("companyadmin.order.company: -"));
    }

    @Test
    void deliveredOrderHasNoActions() {
        var menu = new AdminOrderMenu(messages, economy(), new NoActions());

        var source = menu.chat(viewer, model(OrderState.DELIVERED), buttons).build();

        assertFalse(source.contains("<act_refund>"));
        assertFalse(source.contains("<act_extend>"));
    }

    @Test
    void hoursMustBeAWholeNumberAboveZero() {
        assertEquals(OptionalInt.of(12), AdminOrderMenu.hours(" 12 "));
        assertEquals(OptionalInt.empty(), AdminOrderMenu.hours("0"));
        assertEquals(OptionalInt.empty(), AdminOrderMenu.hours("-3"));
        assertEquals(OptionalInt.empty(), AdminOrderMenu.hours("1.5"));
        assertEquals(OptionalInt.empty(), AdminOrderMenu.hours(null));
    }

    @Test
    void listShowsOwnerAndCompanyOfEveryOrder() {
        var menu = new AdminOrderListMenu(messages, new NoActions(), (clicker, page) -> {});
        var rows = List.of(
                new AdminOrderListMenu.Row(order(OrderState.CLAIMED), "Owner", Optional.of("Iron Works")),
                new AdminOrderListMenu.Row(order(OrderState.UNCLAIMED), "Owner", Optional.empty()));

        var text = menu.chat(viewer, AdminOrderListMenu.Model.of("Owner", 1, rows, 10), buttons);
        var shown = plainText(text);

        assertTrue(shown.contains("#7 <red>Wood"));
        assertTrue(shown.contains("Iron Works"));
        assertTrue(text.build().contains("<act_order_1>"));
        assertFalse(text.build().contains("run_command"));
    }

    @Test
    void playerShowsCompanyOrdersItemsAndNotifications() {
        var renderer = new NotificationRenderer(key -> key);
        var menu = new AdminPlayerMenu(messages, renderer, new NoActions());
        var orders = List.<SimpleOrder>of(order(OrderState.CLAIMED), order(OrderState.RECEIVED));
        var note = new InboxEntry(
                1,
                OWNER,
                NotificationType.PAYMENT,
                NotificationArgs.create().text("order_name", "Wood"),
                CLAIMED,
                true,
                null);
        var model = new AdminPlayerMenu.Model(
                "Owner",
                OWNER,
                Optional.of(
                        new AdminPlayerMenu.Membership("Iron Works", false, List.of(CompanyPermission.MANAGE_ORDERS))),
                orders,
                4,
                2,
                1,
                List.of(note));

        var text = menu.chat(viewer, model, buttons);
        var shown = plainText(text);

        assertTrue(shown.contains("Iron Works"));
        assertTrue(shown.contains("placeholder.role.member"));
        assertTrue(shown.contains("enums.companyPermission.manage_orders"));
        assertTrue(shown.contains("companyadmin.player.workerItems: 4"));
        assertTrue(shown.contains("companyadmin.player.ownerItems: 2"));
        assertTrue(shown.contains("notification.orderPayment"));
        assertTrue(text.build().contains("<act_order_0>"));
        assertFalse(text.build().contains("<act_order_1>"));
        assertEquals(Map.of(OrderState.CLAIMED, 1, OrderState.RECEIVED, 1), model.countsByState());
    }

    @Test
    void playerWithoutCompanySaysSo() {
        var menu = new AdminPlayerMenu(messages, new NotificationRenderer(key -> key), new NoActions());
        var model =
                new AdminPlayerMenu.Model("Loner", UUID.randomUUID(), Optional.empty(), List.of(), 0, 0, 0, List.of());

        var shown = plainText(menu.chat(viewer, model, buttons));

        assertTrue(shown.contains("companyadmin.player.noCompany"));
    }

    private static final class NoActions implements AdminOrderActions {
        @Override
        public void info(Player viewer, int order) {}

        @Override
        public void unclaim(Player viewer, int order) {}

        @Override
        public void cancel(Player viewer, int order, boolean refund) {}

        @Override
        public void deliver(Player viewer, int order) {}

        @Override
        public void extend(Player viewer, int order, @Nullable String entered) {}
    }
}
