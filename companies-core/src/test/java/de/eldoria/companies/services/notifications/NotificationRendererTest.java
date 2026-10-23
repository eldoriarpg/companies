/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRendererTest {
    private static final String HOSTILE = "<click:run_command:/op me>Wood";

    private final NotificationRenderer renderer = new NotificationRenderer(UnaryOperator.identity());
    private Plugin plugin;
    private MessageSender messages;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private Component render(MenuText text) {
        return messages.serializeMessage(text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    private static List<ClickEvent> clicks(Component component) {
        var found = new ArrayList<ClickEvent>();
        collect(component, found);
        return found;
    }

    private static void collect(Component component, List<ClickEvent> found) {
        if (component.clickEvent() != null) found.add(component.clickEvent());
        component.children().forEach(child -> collect(child, found));
    }

    @Test
    void argumentsAreInsertedAsPlainText() {
        var text = renderer.message(
                NotificationType.ORDER_DELIVERED, NotificationArgs.create().text("order_name", HOSTILE));

        assertFalse(text.build().contains(HOSTILE));
        assertTrue(text.build().contains("<i18n:notification.orderDelivered>"));
        assertTrue(clicks(render(text)).isEmpty());
    }

    @Test
    void theActionRunsTheFilledCommand() {
        var text = renderer.message(
                NotificationType.MY_ORDER_DONE,
                NotificationArgs.create().text("order_name", "Wood").number("order_id", 42));

        assertTrue(text.build().contains("<i18n:notification.receiveItems>"));
        assertEquals(List.of(ClickEvent.runCommand("/order receive 42")), clicks(render(text)));
    }

    @Test
    void theBodyHasNoButton() {
        var text = renderer.body(
                NotificationType.MY_ORDER_DONE,
                NotificationArgs.create().text("order_name", "Wood").number("order_id", 42));

        assertTrue(clicks(render(text)).isEmpty());
    }

    @Test
    void legacyEntriesShowTheirStoredMessage() {
        var args = NotificationArgs.create().text(NotificationArgs.LEGACY_MESSAGE, "<red>Old message");

        var text = renderer.message(NotificationType.LEGACY, args);

        assertEquals("Old message", PlainTextComponentSerializer.plainText().serialize(render(text)));
    }

    @Test
    void legacyEntriesWithoutMessageAreEmpty() {
        var text = renderer.message(NotificationType.LEGACY, NotificationArgs.create());

        assertEquals("", PlainTextComponentSerializer.plainText().serialize(render(text)));
    }

    @Test
    void typeIdsAreUnique() {
        var ids = new HashSet<Integer>();
        Arrays.stream(NotificationType.values()).forEach(type -> assertTrue(ids.add(type.id()), type.name()));
        assertEquals(
                NotificationType.MY_ORDER_PURGE_WARNING,
                NotificationType.byId(18).orElseThrow());
    }
}
