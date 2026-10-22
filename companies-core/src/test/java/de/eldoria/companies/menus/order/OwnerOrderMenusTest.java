/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static de.eldoria.companies.menus.order.OrderFixtures.CLAIMED;
import static de.eldoria.companies.menus.order.OrderFixtures.COMPANY;
import static de.eldoria.companies.menus.order.OrderFixtures.WORKER;
import static de.eldoria.companies.menus.order.OrderFixtures.components;
import static de.eldoria.companies.menus.order.OrderFixtures.economy;
import static de.eldoria.companies.menus.order.OrderFixtures.order;
import static de.eldoria.companies.menus.order.OrderFixtures.plain;
import static de.eldoria.companies.menus.order.OrderFixtures.render;
import static de.eldoria.companies.menus.order.OrderFixtures.showsItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerOrderMenusTest {
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions buttons;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        player = server.addPlayer();
        buttons = new MenuActions(plugin, player, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private MenuText info(OrderState state) {
        var model = OwnerOrderMenu.Model.of(order(state), 24, 48, 3, CLAIMED.plusHours(2));
        return new OwnerOrderMenu(new NoActions(), messages, economy()).chat(player, model, buttons);
    }

    private MenuText list(int active, int limit, int waiting) {
        var model = OwnerOrderListMenu.Model.of(
                List.of(order(OrderState.UNCLAIMED)), new OrderLimits.Usage(active, limit), waiting);
        return new OwnerOrderListMenu(new NoActions(), messages, economy()).chat(player, model, buttons);
    }

    @Test
    void infoUsesCallbacksInsteadOfCommands() {
        var text = info(OrderState.UNCLAIMED).build();

        assertFalse(text.contains("run_command"), text);
        assertTrue(text.contains("<act_cancel>"), text);
        assertTrue(text.contains("<act_extend>"), text);
        assertTrue(text.contains("suggest_command:/order raise 7 "), text);
        assertFalse(text.contains("<act_receive>"), text);
    }

    @Test
    void deliveredOrderOffersReceiveOnly() {
        var text = info(OrderState.DELIVERED).build();

        assertFalse(text.contains("run_command"), text);
        assertTrue(text.contains("<act_receive>"), text);
        assertFalse(text.contains("<act_cancel>"), text);
    }

    @Test
    void infoShowsProgressPriceAndTimeLeft() {
        var shown = plain(render(messages, player, info(OrderState.UNCLAIMED)));

        assertTrue(shown.contains("7 | <red>Wood"), shown);
        assertTrue(shown.contains("3/10 $15.00"), shown);
        assertTrue(shown.contains("Named STONE 0/3 $6.00"), shown);
        assertTrue(shown.contains("words.price: $21.00"), shown);
        assertTrue(shown.contains("words.fee: $2.50"), shown);
        assertTrue(shown.contains("words.total: $23.50"), shown);
    }

    @Test
    void exactLinesShowTheRealItem() {
        var rendered = render(messages, player, info(OrderState.UNCLAIMED));

        assertTrue(plain(rendered).contains("Named STONE"));
        assertTrue(showsItem(rendered));
    }

    @Test
    void theOwnerNeverSeesTheCompanyOrItsWorkers() {
        var shown = plain(render(messages, player, info(OrderState.CLAIMED)));

        assertFalse(shown.contains(String.valueOf(COMPANY)), shown);
        assertFalse(shown.contains(WORKER.toString()), shown);
        assertFalse(components(OwnerOrderMenu.Model.class).stream().anyMatch(OwnerOrderMenusTest::revealing));
        assertFalse(components(OrderLineView.class).stream().anyMatch(OwnerOrderMenusTest::revealing));
        assertFalse(components(OrderSummary.class).stream().anyMatch(OwnerOrderMenusTest::revealing));
    }

    @Test
    void timeLeftCountsFromTheLastUpdateOrTheClaim() {
        var now = CLAIMED.plusHours(2);

        assertEquals(
                Duration.ofHours(22),
                OwnerOrderMenu.Model.of(order(OrderState.UNCLAIMED), 24, 48, 3, now)
                        .timeLeft());
        assertEquals(
                Duration.ofHours(46),
                OwnerOrderMenu.Model.of(order(OrderState.CLAIMED), 24, 48, 3, now)
                        .timeLeft());
        assertEquals(
                Duration.ZERO,
                OwnerOrderMenu.Model.of(order(OrderState.DELIVERED), 24, 48, 3, now)
                        .timeLeft());
        assertEquals(
                2,
                OwnerOrderMenu.Model.of(order(OrderState.UNCLAIMED), 24, 48, 3, now)
                        .extensionsLeft());
    }

    @Test
    void raiseAmountMustBeAboveZero() {
        assertEquals(Optional.of(1.5), OwnerOrderMenu.raiseAmount("1,5"));
        assertEquals(Optional.empty(), OwnerOrderMenu.raiseAmount("0"));
        assertEquals(Optional.empty(), OwnerOrderMenu.raiseAmount("-2"));
        assertEquals(Optional.empty(), OwnerOrderMenu.raiseAmount("much"));
        assertEquals(Optional.empty(), OwnerOrderMenu.raiseAmount(null));
    }

    @Test
    void listShowsLimitWaitingItemsAndOrders() {
        var text = list(1, 5, 12);
        var shown = plain(render(messages, player, text));

        assertFalse(text.build().contains("run_command"), text.build());
        assertTrue(text.build().contains("<act_collect>"), text.build());
        assertTrue(text.build().contains("<act_info_0>"), text.build());
        assertTrue(text.build().contains("suggest_command:/order create "), text.build());
        assertTrue(shown.contains("order.list.orders (1/5):"), shown);
        assertTrue(shown.contains("7 | <red>Wood [words.info]"), shown);
    }

    @Test
    void listWithoutWaitingItemsOrRoomForOrders() {
        var text = list(5, 5, 0).build();

        assertFalse(text.contains("<act_collect>"), text);
        assertFalse(text.contains("/order create"), text);
        assertTrue(text.contains("order.list.limitReached"), text);
    }

    private static boolean revealing(String component) {
        return component.contains("company") || component.contains("owner") || component.contains("worker");
    }

    private static final class NoActions implements OwnerOrderActions {
        @Override
        public void info(Player player, int id) {}

        @Override
        public void list(Player player) {}

        @Override
        public void cancel(Player player, int id) {}

        @Override
        public void extend(Player player, int id) {}

        @Override
        public void raise(Player player, int id, @Nullable String entered) {}

        @Override
        public void receive(Player player, int id) {}

        @Override
        public void collect(Player player) {}

        @Override
        public void create(Player player, @Nullable String name) {}
    }
}
