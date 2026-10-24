/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.services.notifications.InboxPage;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationCategory;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.Times;
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
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxMenusTest {
    @BeforeEach
    void useServerZone() {
        Times.useZone(() -> ZoneOffset.UTC);
    }

    @AfterEach
    void useMachineZone() {
        Times.useZone(Times::machineZone);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final String HOSTILE = "<red>Wood";

    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
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

    private InboxMenu inboxMenu() {
        return new InboxMenu(messages, new NotificationRenderer(UnaryOperator.identity()), new NoInboxActions());
    }

    private static InboxEntry entry(long id, LocalDateTime created, boolean read) {
        return new InboxEntry(
                id,
                UUID.randomUUID(),
                NotificationType.ORDER_DELIVERED,
                NotificationArgs.create().text("order_name", HOSTILE),
                created,
                true,
                read ? created : null);
    }

    @Test
    void entriesAreGroupedByDayWithTheUnreadMarked() {
        var page = new InboxPage(
                List.of(
                        entry(3, NOW.minusHours(1), false),
                        entry(2, NOW.minusDays(1), true),
                        entry(1, NOW.minusDays(5), true)),
                1,
                true,
                NOW);

        var text = inboxMenu().chat(player, InboxMenu.Model.of(page), actions);

        var built = text.build();
        assertTrue(built.contains("<act_read_all>"));
        assertTrue(built.contains("<act_clear>"));
        assertTrue(built.contains("<act_settings>"));
        assertTrue(built.contains("<act_next>"));
        assertFalse(built.contains("<act_previous>"));
        assertFalse(built.contains(HOSTILE));
        assertEquals(1, built.split("i18n:inbox.unread", -1).length - 1);
        assertTrue(built.contains("<i18n:words.today>"));
        assertTrue(built.contains("<i18n:words.yesterday>"));
        var shown = plain(text);
        assertTrue(shown.contains("05.03.:"));
        assertTrue(shown.contains("11:00"));
        assertEquals(3, shown.split("notification.orderDelivered", -1).length - 1);
    }

    @Test
    void theNoticeIsShownOnTop() {
        var page = new InboxPage(List.of(), 2, false, NOW);
        var notice = MenuText.create().text("<neutral>Done");

        var text = inboxMenu().chat(player, new InboxMenu.Model(page, Optional.of(notice)), actions);

        assertTrue(plain(text).startsWith("Done"));
        assertTrue(text.build().contains("<act_previous>"));
        assertTrue(text.build().contains("inbox.empty"));
    }

    @Test
    void aSinglePageHasNoPaging() {
        var page = new InboxPage(List.of(entry(1, NOW, false)), 1, false, NOW);

        var built = inboxMenu().chat(player, InboxMenu.Model.of(page), actions).build();

        assertFalse(built.contains("<act_next>"));
        assertFalse(built.contains("<act_previous>"));
    }

    @Test
    void settingsHaveOneSwitchPerMutableCategory() {
        var menu = new InboxSettingsMenu(messages, new NoSettingsActions());
        var model = new InboxSettingsMenu.Model(NotificationCategory.PAYMENTS.bit());

        var built = menu.chat(player, model, actions).build();

        for (var category : NotificationCategory.mutables()) {
            assertTrue(built.contains(category.key()), category.name());
        }
        assertFalse(built.contains(NotificationCategory.INVITES.key()));
        assertTrue(built.contains("<toggle_payments><u><inactive><i18n:words.off>"));
        assertTrue(built.contains("<toggle_orders><u><active><i18n:words.on>"));
        assertTrue(built.contains("<act_back>"));
        assertFalse(built.contains("run_command"));
    }

    private static final class NoInboxActions implements InboxMenu.Handlers {
        @Override
        public void page(Player player, int number) {}

        @Override
        public void readAll(Player player) {}

        @Override
        public void clearRead(Player player) {}

        @Override
        public void settings(Player player) {}
    }

    private static final class NoSettingsActions implements InboxSettingsMenu.Handlers {
        @Override
        public void toggle(Player player, NotificationCategory category) {}

        @Override
        public void back(Player player) {}
    }
}
