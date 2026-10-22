/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.eldoutilities.localization.IMessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Shows menus as a dialog or in chat, see {@link MenuMode}.
 * <p>
 * The chat rendering is the only place that holds a player's chat: it blocks the chat, pushes older messages out of
 * view, adds the {@code [x]} button that releases the chat when it is held, and sends the menu past the blocker.
 * Dialogs never hold chat. Menus may be shown from any thread.
 */
public final class MenuService {
    /**
     * The button that releases the held chat.
     */
    static final String CLOSE = "<click:run_command:/company chatblock false><red>[x]</red></click>";

    private static final int CLEARED_LINES = 25;

    private final MessageBlocker blocker;
    private final Supplier<MenuMode> configured;

    /**
     * @param blocker    message blocker of the plugin
     * @param configured the configured mode, read on every showing so a reload applies at once
     */
    public MenuService(MessageBlocker blocker, Supplier<MenuMode> configured) {
        this.blocker = blocker;
        this.configured = configured;
    }

    /**
     * Shows a menu as a dialog when possible, otherwise in chat.
     *
     * @param player player to show the menu to
     * @param menu   the menu
     * @param model  what the menu shows
     * @param <M>    type of the view model
     */
    public <M> void show(Player player, Menu<M> menu, M model) {
        var actions = new MenuActions(plugin(), player, menu.lifetime());
        var dialog = dialog(player, menu, model, actions);
        if (dialog.isPresent()) {
            showDialog(player, dialog.get());
            return;
        }
        showChat(player, menu.chat(player, model, actions));
    }

    /**
     * Shows a menu that exists only as chat text.
     *
     * @param player  player to show the menu to
     * @param content text of the menu
     */
    // TODO remove once every menu is a Menu with named placeholders
    public void show(Player player, IMessageComposer content) {
        showChat(player, content);
    }

    /**
     * Closes an open dialog and releases the held chat, delivering the messages held meanwhile.
     *
     * @param player player whose menu is closed
     * @return completes once the held messages were delivered
     */
    public CompletableFuture<Void> close(Player player) {
        if (MenuMode.dialogsPossible(configured.get(), player.getProtocolVersion())) {
            onMainThread(player::closeDialog);
        }
        return blocker.unblockPlayer(player);
    }

    /**
     * The mode a menu is shown in for a player.
     *
     * @param player        viewing player
     * @param menuHasDialog whether the menu provides a dialog
     * @return the mode
     */
    public MenuMode mode(Player player, boolean menuHasDialog) {
        return MenuMode.select(configured.get(), player.getProtocolVersion(), menuHasDialog);
    }

    private <M> Optional<Dialog> dialog(Player player, Menu<M> menu, M model, MenuActions actions) {
        if (!MenuMode.dialogsPossible(configured.get(), player.getProtocolVersion())) return Optional.empty();
        return menu.dialog(player, model, actions);
    }

    private void showDialog(Player player, Dialog dialog) {
        onMainThread(() -> player.showDialog(dialog));
    }

    private void showChat(Player player, IMessageComposer content) {
        blocker.blockPlayer(player);
        var text = new StringBuilder("\n".repeat(CLEARED_LINES)).append(content.build());
        if (blocker.isBlocked(player)) {
            text.append('\n').append(CLOSE);
        }
        var sender = MessageSender.getPluginMessageSender(plugin());
        var rendered = sender.serializeMessage(
                player, text.toString(), content.replacements().toArray(TagResolver[]::new));
        blocker.sendBypassing(player, sender.prefix().appendSpace().append(rendered));
    }

    private void onMainThread(Runnable task) {
        var plugin = plugin();
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private Plugin plugin() {
        return blocker.plugin();
    }
}
