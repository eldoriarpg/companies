/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.order.OrderFixtures;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertsMenuTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final OrderFilter IRON = OrderFilter.ALL
            .withMaterials(List.of("iron_ingot", "<red>gold"))
            .withMaterialMode(false, true)
            .withPrice(10, 500)
            .withSize(0, 256);

    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        player = server.addPlayer("Viewer");
        actions = new MenuActions(plugin, player, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        player, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    private static OrderAlert alert(int id, String name, OrderFilter filter, boolean enabled) {
        return new OrderAlert(
                id,
                7,
                name,
                filter,
                UUID.randomUUID(),
                enabled,
                NOW.minusDays(1),
                id == 1 ? Optional.of(NOW.minusMinutes(90)) : Optional.empty(),
                0);
    }

    private MenuText chat(boolean mayManage, OrderAlert... alerts) {
        var model = new AlertsMenu.Model(List.of(alerts), 5, mayManage, NOW, Optional.empty());
        return new AlertsMenu(messages, OrderFixtures.economy(), new NoActions()).chat(player, model, actions);
    }

    @Test
    void managersGetButtonsToSwitchAndRemoveAlerts() {
        var text = chat(true, alert(1, "iron", IRON, true), alert(2, "all", OrderFilter.ALL, false));

        var built = text.build();
        assertFalse(built.contains("run_command"));
        assertTrue(built.contains("<act_show_0>"));
        assertTrue(built.contains("<act_toggle_1>"));
        assertTrue(built.contains("<act_remove_1>"));
        assertTrue(built.contains("<act_search>"));
    }

    @Test
    void otherMembersOnlyShowAlerts() {
        var built = chat(false, alert(1, "iron", IRON, true)).build();

        assertTrue(built.contains("<act_show_0>"));
        assertFalse(built.contains("<act_toggle_0>"));
        assertFalse(built.contains("<act_remove_0>"));
    }

    @Test
    void eachAlertShowsItsFilterStateAndLastNotice() {
        var shown = plain(chat(true, alert(1, "iron", IRON, true), alert(2, "all", OrderFilter.ALL, false)));

        assertTrue(shown.contains("company.alerts.count"), shown);
        assertTrue(shown.contains("iron_ingot, <red>gold"), shown);
        assertTrue(shown.contains("$10.00–$500.00"), shown);
        assertTrue(shown.contains("≤ 256"), shown);
        assertTrue(shown.contains("1h 30m"), shown);
        assertTrue(shown.contains("company.alerts.anyOrder"), shown);
        assertTrue(shown.contains("company.alerts.neverNotified"), shown);
    }

    @Test
    void anEmptyListSaysSo() {
        assertTrue(chat(true).build().contains("company.alerts.none"));
    }

    @Test
    void rangesShowOnlyTheirBounds() {
        assertEquals(Optional.empty(), AlertsMenu.range(0, 100, 100, String::valueOf));
        assertEquals(Optional.of("≥ 5"), AlertsMenu.range(5, 100, 100, value -> format(value)));
        assertEquals(Optional.of("≤ 50"), AlertsMenu.range(0, 50, 100, value -> format(value)));
        assertEquals(Optional.of("5–50"), AlertsMenu.range(5, 50, 100, value -> format(value)));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static final class NoActions implements AlertsMenu.Handlers {
        @Override
        public void show(Player player, OrderAlert alert) {}

        @Override
        public void toggle(Player player, OrderAlert alert) {}

        @Override
        public void remove(Player player, OrderAlert alert) {}

        @Override
        public void search(Player player) {}
    }
}
