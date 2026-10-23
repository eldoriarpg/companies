/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.order.OrderLineView;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import static de.eldoria.companies.menus.order.OrderFixtures.CLAIMED;
import static de.eldoria.companies.menus.order.OrderFixtures.OWNER;
import static de.eldoria.companies.menus.order.OrderFixtures.components;
import static de.eldoria.companies.menus.order.OrderFixtures.economy;
import static de.eldoria.companies.menus.order.OrderFixtures.order;
import static de.eldoria.companies.menus.order.OrderFixtures.plain;
import static de.eldoria.companies.menus.order.OrderFixtures.render;
import static de.eldoria.companies.menus.order.OrderFixtures.showsItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyOrderMenusTest {
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

    private static CompanyOrderMenu.Model model(OrderState state, boolean canManage) {
        var order = order(state);
        return new CompanyOrderMenu.Model(
                order.id(),
                order.name(),
                state,
                OrderLineView.of(order),
                order.price(),
                5,
                Duration.ofHours(3),
                canManage);
    }

    private MenuText info(CompanyOrderMenu.Model model) {
        return new CompanyOrderMenu(new NoActions(), messages, economy()).chat(player, model, buttons);
    }

    @Test
    void claimedOrderDeliversThroughCallbacks() {
        var text = info(model(OrderState.CLAIMED, true)).build();

        assertFalse(text.contains("run_command"), text);
        assertTrue(text.contains("<act_line_1_1>"), text);
        assertTrue(text.contains("<act_line_1_64>"), text);
        assertTrue(text.contains("<act_line_2_max>"), text);
        assertTrue(text.contains("suggest_command:/company order deliver 7 1 "), text);
        assertTrue(text.contains("<act_deliver_all>"), text);
        assertTrue(text.contains("<act_abort>"), text);
        assertFalse(text.contains("<act_accept>"), text);
    }

    @Test
    void membersWithoutManagePermissionCanNotAbortOrAccept() {
        assertFalse(info(model(OrderState.CLAIMED, false)).build().contains("<act_abort>"));
        assertFalse(info(model(OrderState.UNCLAIMED, false)).build().contains("<act_accept>"));
        assertTrue(info(model(OrderState.UNCLAIMED, true)).build().contains("<act_accept>"));
    }

    @Test
    void unclaimedOrdersShowRequestedAmountsWithoutDeliverButtons() {
        var text = info(model(OrderState.UNCLAIMED, true));
        var shown = plain(render(messages, player, text));

        assertFalse(text.build().contains("<act_line_1_1>"), text.build());
        assertTrue(shown.contains("10x $15.00"), shown);
    }

    @Test
    void infoShowsPriceTaxAndTimeLeft() {
        var shown = plain(render(messages, player, info(model(OrderState.CLAIMED, true))));

        assertTrue(shown.contains("7 | <red>Wood"), shown);
        assertTrue(shown.contains("3/10 $15.00"), shown);
        assertTrue(shown.contains("words.price: $21.00"), shown);
        assertTrue(shown.contains("words.tax: $1.05 (5 %)"), shown);
        assertTrue(shown.contains("words.net: $19.95"), shown);
        assertTrue(shown.contains("words.leftTime 3:0"), shown);
    }

    @Test
    void finishedLinesAreDoneAndShowTheirDamage() {
        var line = new OrderLineView(1, new ItemStack(Material.IRON_PICKAXE), false, 4, 4, 8, 2, 0.5);
        var model = new CompanyOrderMenu.Model(
                3, "Tools", OrderState.CLAIMED, List.of(line), 8, 0, Duration.ofHours(1), true);

        var text = info(model);
        var shown = plain(render(messages, player, text));

        assertTrue(shown.contains("words.done company.order.info.damaged: 2 (50 %)"), shown);
        assertFalse(text.build().contains("<act_line_1_1>"), text.build());
        assertFalse(text.build().contains("<act_deliver_all>"), text.build());
    }

    @Test
    void exactLinesShowTheRealItem() {
        var rendered = render(messages, player, info(model(OrderState.CLAIMED, true)));

        assertTrue(plain(rendered).contains("Named STONE"));
        assertTrue(showsItem(rendered));
    }

    @Test
    void theCompanyNeverSeesTheOwner() {
        var shown = plain(render(messages, player, info(model(OrderState.CLAIMED, true))));

        assertFalse(shown.contains(OWNER.toString()), shown);
        assertFalse(components(CompanyOrderMenu.Model.class).contains("owner"));
        assertFalse(components(CompanyOrderListMenu.Model.class).contains("owner"));
    }

    @Test
    void modelCountsTheDeliveryTimeFromTheClaim() {
        var matcher = ItemMatcher.of(new ItemMatchSettings(List.of(), false), Logger.getAnonymousLogger());
        var model = CompanyOrderMenu.Model.of(order(OrderState.CLAIMED), 48, 5, true, matcher, CLAIMED.plusHours(8));

        assertEquals(Duration.ofHours(40), model.timeLeft());
        assertEquals(0, model.lines().getFirst().damaged());
    }

    @Test
    void listUsesCallbacksInsteadOfCommands() {
        var model = CompanyOrderListMenu.Model.of(List.of(order(OrderState.CLAIMED)), 5);

        var text = new CompanyOrderListMenu(new NoActions(), messages, economy()).chat(player, model, buttons);
        var shown = plain(render(messages, player, text));

        assertFalse(text.build().contains("run_command"), text.build());
        assertTrue(text.build().contains("<act_search>"), text.build());
        assertTrue(text.build().contains("<act_info_0>"), text.build());
        assertTrue(shown.contains("7 | <red>Wood [words.info]"), shown);
    }

    @Test
    void soloActionsSuggestTheirOwnCommandsAndTitle() {
        var text = new CompanyOrderMenu(new SoloActions(), messages, economy())
                .chat(player, model(OrderState.CLAIMED, true), buttons)
                .build();
        var list = new CompanyOrderListMenu(new SoloActions(), messages, economy())
                .chat(player, CompanyOrderListMenu.Model.of(List.of(order(OrderState.CLAIMED)), 0), buttons)
                .build();

        assertTrue(text.contains("suggest_command:/order work deliver 7 1 "), text);
        assertTrue(list.contains("<i18n:solo.list.title>"), list);
        assertFalse(list.contains("company.order.list.orders"), list);
    }

    private static final class SoloActions implements CompanyOrderActions {
        @Override
        public String deliverCommand() {
            return "/order work deliver";
        }

        @Override
        public String listTitle() {
            return "solo.list.title";
        }

        @Override
        public void info(Player player, int id) {}

        @Override
        public void list(Player player) {}

        @Override
        public void search(Player player) {}

        @Override
        public void deliver(Player player, int id, int line, int amount) {}

        @Override
        public void deliverAll(Player player, int id) {}

        @Override
        public void accept(Player player, int id) {}

        @Override
        public void abort(Player player, int id) {}
    }

    private static final class NoActions implements CompanyOrderActions {
        @Override
        public void info(Player player, int id) {}

        @Override
        public void list(Player player) {}

        @Override
        public void search(Player player) {}

        @Override
        public void deliver(Player player, int id, int line, int amount) {}

        @Override
        public void deliverAll(Player player, int id) {}

        @Override
        public void accept(Player player, int id) {}

        @Override
        public void abort(Player player, int id) {}
    }
}
