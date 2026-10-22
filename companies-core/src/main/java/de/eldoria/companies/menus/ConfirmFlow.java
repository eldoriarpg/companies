/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.util.Confirmations;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Asks players to confirm an action through the {@link ConfirmMenu} and performs it once they do.
 * <p>
 * {@link Confirmations} decides what is still valid: a confirmation lasts 30 seconds and is used once. The buttons of
 * a menu confirm only the request they were shown for; the typed {@code confirm} command confirms whatever is
 * pending. Confirming too late, twice or after denying tells the player the confirmation expired.
 *
 * @param <T> the value carried until the player confirms
 */
public final class ConfirmFlow<T> {
    private final Confirmations<T> confirmations;
    private final ConfirmMenu menu;
    private final BiConsumer<Player, T> onConfirmed;
    private final Consumer<Player> onExpired;
    private final Consumer<Player> onCanceled;

    /**
     * @param confirmations the pending confirmations
     * @param menu          menu asking the player
     * @param onConfirmed   performs the confirmed action
     * @param onExpired     tells the player nothing valid was left to confirm
     * @param onCanceled    tells the player the action was canceled
     */
    ConfirmFlow(
            Confirmations<T> confirmations,
            ConfirmMenu menu,
            BiConsumer<Player, T> onConfirmed,
            Consumer<Player> onExpired,
            Consumer<Player> onCanceled) {
        this.confirmations = confirmations;
        this.menu = menu;
        this.onConfirmed = onConfirmed;
        this.onExpired = onExpired;
        this.onCanceled = onCanceled;
    }

    /**
     * Creates a flow answering with {@code error.confirmationExpired} and {@code words.canceled}.
     *
     * @param plugin      the plugin
     * @param onConfirmed performs the confirmed action, on the main thread
     * @param <T>         the value carried until the player confirms
     * @return the flow
     */
    public static <T> ConfirmFlow<T> create(Plugin plugin, BiConsumer<Player, T> onConfirmed) {
        return new ConfirmFlow<>(
                new Confirmations<>(),
                new ConfirmMenu(plugin),
                onConfirmed,
                player -> MessageSender.getPluginMessageSender(plugin)
                        .sendErrorActionBar(player, "error.confirmationExpired"),
                player -> MessageSender.getPluginMessageSender(plugin).sendMessage(player, "words.canceled"));
    }

    /**
     * Asks a player to confirm a value, replacing what the player was asked before.
     *
     * @param menus    shows the menu
     * @param player   player who has to confirm
     * @param value    value handed to the action once confirmed
     * @param question what the player is asked
     */
    public void ask(MenuService menus, Player player, T value, MenuText question) {
        menus.show(player, menu, prompt(player, value, question));
    }

    /**
     * Requests the confirmation and binds the answers to exactly this request.
     *
     * @param player   player who has to confirm
     * @param value    value handed to the action once confirmed
     * @param question what the player is asked
     * @return the model of the menu
     */
    ConfirmMenu.Prompt prompt(Player player, T value, MenuText question) {
        var ticket = confirmations.request(player.getUniqueId(), value);
        return new ConfirmMenu.Prompt(
                question, clicked -> settle(clicked, confirmations.confirm(clicked.getUniqueId(), ticket)), clicked -> {
                    confirmations.confirm(clicked.getUniqueId(), ticket);
                    onCanceled.accept(clicked);
                });
    }

    /**
     * Performs what the player was asked to confirm, as the typed {@code confirm} command does.
     *
     * @param player player who confirms
     */
    public void confirm(Player player) {
        settle(player, confirmations.confirm(player.getUniqueId()));
    }

    /**
     * Drops what the player was asked to confirm, as the typed {@code deny} command does.
     *
     * @param player player who denies
     */
    public void cancel(Player player) {
        confirmations.confirm(player.getUniqueId());
        onCanceled.accept(player);
    }

    private void settle(Player player, Optional<T> value) {
        value.ifPresentOrElse(confirmed -> onConfirmed.accept(player, confirmed), () -> onExpired.accept(player));
    }
}
