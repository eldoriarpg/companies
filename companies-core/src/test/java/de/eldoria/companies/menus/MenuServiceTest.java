/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuServiceTest {
    private static final int OLD_CLIENT = 770;

    private ServerMock server;
    private Plugin plugin;
    private RecordingBlocker blocker;
    private MenuMode configured;
    private MenuService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        blocker = new RecordingBlocker(plugin);
        configured = MenuMode.CHAT;
        service = new MenuService(blocker, () -> configured);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private Client client(int protocol) {
        var client = new Client(server, protocol);
        server.addPlayer(client);
        return client;
    }

    private static MessageComposer orders() {
        return MessageComposer.create()
                .text("<heading>")
                .localeCode("company.order.list.orders", Placeholder.unparsed("order_name", "<red>Wood"))
                .text(": <click:run_command:/company order search query><show>[")
                .localeCode("words.search")
                .text("]</click> 100% <order_name>")
                .newLine();
    }

    private static MenuText ordersText() {
        return MenuText.create()
                .text("<heading>")
                .locale("company.order.list.orders", Values.text("order_name", "<red>Wood"))
                .text(": <click:run_command:/company order search query><show>[")
                .locale("words.search")
                .text("]</click> 100% <order_name>")
                .newLine();
    }

    /**
     * The chat menu as the commands built it before the menu service, without the close button: 25 empty lines, then
     * {@code Menus.show}.
     */
    private static Component previousShow(MessageBlocker blocker, Player player, MessageComposer composer) {
        composer.prependLines(25);
        var sender = MessageSender.getPluginMessageSender(blocker.plugin());
        var content = sender.serializeMessage(
                player, composer.build(), composer.replacements().toArray(TagResolver[]::new));
        return sender.prefix().appendSpace().append(content);
    }

    @Test
    void heldChatEndsWithACloseCallback() {
        var player = client(MenuMode.DIALOG_PROTOCOL);
        var menuText = PlainTextComponentSerializer.plainText()
                .serialize(previousShow(MessageBlocker.dummy(plugin), player, orders()));

        service.show(player, new TestMenu(Optional.empty()), "model");

        assertTrue(blocker.isBlocked(player));
        assertEquals(1, blocker.sent.size());
        var sent = blocker.sent.getFirst();
        assertEquals(
                menuText + "\n[x]", PlainTextComponentSerializer.plainText().serialize(sent));
        assertFalse(MiniMessage.miniMessage().serialize(sent).contains("chatblock"));
    }

    @Test
    void withoutBlockingThereIsNoCloseButton() {
        var player = client(MenuMode.DIALOG_PROTOCOL);
        var expected = previousShow(MessageBlocker.dummy(plugin), player, orders());
        player.nextComponentMessage();

        new MenuService(MessageBlocker.dummy(plugin), () -> MenuMode.CHAT)
                .show(player, new TestMenu(Optional.empty()), "model");

        assertEquals(expected, player.nextComponentMessage());
        assertNull(player.nextComponentMessage());
    }

    @Test
    void chatModeNeverAsksForADialog() {
        var player = client(MenuMode.DIALOG_PROTOCOL);
        var menu = new TestMenu(Optional.of(new TestDialog()));

        service.show(player, menu, "model");

        assertEquals(0, menu.dialogRequests.get());
        assertEquals(1, blocker.sent.size());
        assertTrue(player.dialogs.isEmpty());
    }

    @Test
    void menuWithoutDialogFallsBackToChat() {
        configured = MenuMode.DIALOG;
        var player = client(MenuMode.DIALOG_PROTOCOL);
        var menu = new TestMenu(Optional.empty());

        service.show(player, menu, "model");

        assertEquals(1, menu.dialogRequests.get());
        assertEquals(1, blocker.sent.size());
        assertTrue(player.dialogs.isEmpty());
    }

    @Test
    void oldClientFallsBackToChat() {
        configured = MenuMode.DIALOG;
        var player = client(OLD_CLIENT);
        var menu = new TestMenu(Optional.of(new TestDialog()));

        service.show(player, menu, "model");

        assertEquals(0, menu.dialogRequests.get());
        assertEquals(1, blocker.sent.size());
        assertTrue(player.dialogs.isEmpty());
    }

    @Test
    void dialogIsShownWithoutHoldingTheChat() {
        configured = MenuMode.DIALOG;
        var player = client(MenuMode.DIALOG_PROTOCOL);
        var dialog = new TestDialog();

        service.show(player, new TestMenu(Optional.of(dialog)), "model");

        assertEquals(1, player.dialogs.size());
        assertSame(dialog, player.dialogs.getFirst());
        assertTrue(blocker.sent.isEmpty());
        assertFalse(blocker.isBlocked(player));
    }

    @Test
    void modeFollowsTheConfiguration() {
        var player = client(MenuMode.DIALOG_PROTOCOL);
        assertEquals(MenuMode.CHAT, service.mode(player, true));

        configured = MenuMode.DIALOG;

        assertEquals(MenuMode.DIALOG, service.mode(player, true));
        assertEquals(MenuMode.CHAT, service.mode(player, false));
    }

    @Test
    void closeReleasesTheChat() {
        var player = client(OLD_CLIENT);
        service.show(player, new TestMenu(Optional.empty()), "model");

        service.close(player).join();

        assertFalse(blocker.isBlocked(player));
    }

    private static final class TestMenu implements Menu<String> {
        private final Optional<Dialog> dialog;
        private final AtomicInteger dialogRequests = new AtomicInteger();

        private TestMenu(Optional<Dialog> dialog) {
            this.dialog = dialog;
        }

        @Override
        public MenuText chat(Player player, String model, MenuActions actions) {
            return ordersText();
        }

        @Override
        public Optional<Dialog> dialog(Player player, String model, MenuActions actions) {
            dialogRequests.incrementAndGet();
            return dialog;
        }
    }

    @SuppressWarnings("removal")
    private static final class TestDialog implements Dialog {
        @Override
        public NamespacedKey getKey() {
            return NamespacedKey.minecraft("test");
        }
    }

    private static final class Client extends PlayerMock {
        private final int protocol;
        private final List<DialogLike> dialogs = new ArrayList<>();

        private Client(ServerMock server, int protocol) {
            super(server, "Client");
            this.protocol = protocol;
        }

        @Override
        public int getProtocolVersion() {
            return protocol;
        }

        @Override
        public void showDialog(DialogLike dialog) {
            dialogs.add(dialog);
        }
    }

    private static final class RecordingBlocker implements MessageBlocker {
        private final Plugin plugin;
        private final Set<UUID> blocked = new HashSet<>();
        private final List<Component> sent = new ArrayList<>();

        private RecordingBlocker(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void blockPlayer(Player player) {
            blocked.add(player.getUniqueId());
        }

        @Override
        public CompletableFuture<Void> unblockPlayer(Player player) {
            blocked.remove(player.getUniqueId());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isBlocked(Player player) {
            return blocked.contains(player.getUniqueId());
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
