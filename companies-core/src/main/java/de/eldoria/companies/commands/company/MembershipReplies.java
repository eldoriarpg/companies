/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.MembershipSources;
import de.eldoria.companies.services.MembershipService.Outcome;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Answers the membership commands and menu buttons with the outcome of their action.
 * <p>
 * A button closes its menu before the outcome is sent, so a held chat is released and the message is seen. Every
 * successful action drops the cached suggestions of the player.
 */
final class MembershipReplies {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    MembershipReplies(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Sends the outcome of an action of a command.
     *
     * @param player acting player
     * @param action the running action
     */
    void reply(Player player, CompletableFuture<Outcome> action) {
        action.thenAccept(outcome -> send(player, outcome)).exceptionally(err -> log(err));
    }

    /**
     * Closes the menu a button belongs to and sends the outcome of the button's action.
     *
     * @param player acting player
     * @param action the running action
     */
    void replyToButton(Player player, CompletableFuture<Outcome> action) {
        action.thenAccept(outcome -> deps.services().menus().close(player).thenRun(() -> send(player, outcome)))
                .exceptionally(err -> log(err));
    }

    /**
     * Runs an action on an entry the player picked by name, or refuses when there is no such entry.
     *
     * @param player  acting player
     * @param entry   finds the entry
     * @param refusal outcome when there is no entry
     * @param action  action on the entry
     * @param <T>     type of the entry
     */
    <T> void replyTo(
            Player player,
            CompletableFuture<Optional<T>> entry,
            Outcome refusal,
            Function<T, CompletableFuture<Outcome>> action) {
        reply(player, entry.thenCompose(found -> found.map(action)
                .orElseGet(() -> CompletableFuture.completedFuture(refusal))));
    }

    private void send(Player player, Outcome outcome) {
        if (outcome.success()) MembershipSources.invalidate(player.getUniqueId());
        outcome.sendTo(player, deps.messages());
    }

    private Void log(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }
}
