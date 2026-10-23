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
     * Asks a player to confirm a value, offering a second way to confirm next to the confirm button.
     *
     * @param menus        shows the menu
     * @param player       player who has to confirm
     * @param value        value handed to the chosen action once confirmed
     * @param question     what the player is asked
     * @param confirmLabel text of the confirm button
     * @param alternative  the second way to confirm
     */
    public void ask(
            MenuService menus,
            Player player,
            T value,
            MenuText question,
            MenuText confirmLabel,
            Alternative<T> alternative) {
        menus.show(player, menu, prompt(player, value, question, confirmLabel, alternative));
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
        return new ConfirmMenu.Prompt(question, confirmer(ticket, onConfirmed), canceler(ticket));
    }

    /**
     * Requests the confirmation with a second way to confirm and binds every answer to exactly this request.
     *
     * @param player       player who has to confirm
     * @param value        value handed to the chosen action once confirmed
     * @param question     what the player is asked
     * @param confirmLabel text of the confirm button
     * @param alternative  the second way to confirm
     * @return the model of the menu
     */
    ConfirmMenu.Prompt prompt(
            Player player, T value, MenuText question, MenuText confirmLabel, Alternative<T> alternative) {
        var ticket = confirmations.request(player.getUniqueId(), value);
        var choice = new ConfirmMenu.Choice(alternative.label(), confirmer(ticket, alternative.onConfirmed()));
        return new ConfirmMenu.Prompt(
                question, confirmer(ticket, onConfirmed), canceler(ticket), confirmLabel, Optional.of(choice));
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
     * Performs the second way to confirm on what the player was asked to confirm, as a typed command does.
     *
     * @param player player who confirms
     * @param action the action of the second way
     */
    public void confirmWith(Player player, BiConsumer<Player, T> action) {
        settle(player, confirmations.confirm(player.getUniqueId()), action);
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

    private Consumer<Player> confirmer(Confirmations.Ticket ticket, BiConsumer<Player, T> action) {
        return clicked -> settle(clicked, confirmations.confirm(clicked.getUniqueId(), ticket), action);
    }

    private Consumer<Player> canceler(Confirmations.Ticket ticket) {
        return clicked -> {
            confirmations.confirm(clicked.getUniqueId(), ticket);
            onCanceled.accept(clicked);
        };
    }

    private void settle(Player player, Optional<T> value) {
        settle(player, value, onConfirmed);
    }

    private void settle(Player player, Optional<T> value, BiConsumer<Player, T> action) {
        value.ifPresentOrElse(confirmed -> action.accept(player, confirmed), () -> onExpired.accept(player));
    }

    /**
     * A second way to confirm, such as paying from the treasury instead of paying oneself.
     *
     * @param label       text of its button
     * @param onConfirmed performs the action this way, on the main thread
     * @param <T>         the value carried until the player confirms
     */
    public record Alternative<T>(MenuText label, BiConsumer<Player, T> onConfirmed) {}
}
