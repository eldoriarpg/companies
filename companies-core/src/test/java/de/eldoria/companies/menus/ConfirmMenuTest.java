/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.text.Component;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmMenuTest {
    private ServerMock server;
    private Plugin plugin;
    private ConfirmMenu menu;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        menu = new ConfirmMenu(plugin);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static ConfirmMenu.Prompt prompt() {
        return new ConfirmMenu.Prompt(
                MenuText.create().text("Kick <member_name>?", Values.text("member_name", "<red>Steve")),
                player -> {},
                player -> {});
    }

    @Test
    void chatLineUsesCallbacksInsteadOfCommands() {
        var player = server.addPlayer();

        var text = menu.chat(player, prompt(), new MenuActions(plugin, player, menu.lifetime()));

        assertFalse(text.build().contains("run_command"));
        assertTrue(text.build().contains("<act_confirm>"));
        assertTrue(text.build().contains("<act_deny>"));
        var keys = text.replacements().stream()
                .map(value -> ((TagResolver.Single) value).key())
                .toList();
        assertEquals(List.of("member_name", ConfirmMenu.CONFIRM, ConfirmMenu.DENY), keys);
    }

    @Test
    void buttonsExpireWithTheConfirmation() {
        assertEquals(Confirmations.VALIDITY, menu.lifetime());
        assertEquals(Duration.ofSeconds(30), menu.lifetime());
    }

    @Test
    void chatLineDoesNotHoldTheChat() {
        var player = new Client(server);
        server.addPlayer(player);
        var blocker = new RecordingBlocker(plugin);

        new MenuService(blocker, () -> MenuMode.CHAT).show(player, menu, prompt());

        assertFalse(blocker.blockRequested);
        assertEquals(1, blocker.sent.size());
        var line = PlainTextComponentSerializer.plainText().serialize(blocker.sent.getFirst());
        assertFalse(line.contains("\n"));
        assertTrue(line.contains("Kick <red>Steve?"));
    }

    private static final class Client extends PlayerMock {
        private Client(ServerMock server) {
            super(server, "Client");
        }

        @Override
        public int getProtocolVersion() {
            return MenuMode.DIALOG_PROTOCOL;
        }
    }

    private static final class RecordingBlocker implements MessageBlocker {
        private final Plugin plugin;
        private final List<Component> sent = new ArrayList<>();
        private boolean blockRequested;

        private RecordingBlocker(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void blockPlayer(Player player) {
            blockRequested = true;
        }

        @Override
        public CompletableFuture<Void> unblockPlayer(Player player) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isBlocked(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void sendBypassing(Player player, Component message) {
            sent.add(message);
        }

        @Override
        public Plugin plugin() {
            return plugin;
        }
    }
}
