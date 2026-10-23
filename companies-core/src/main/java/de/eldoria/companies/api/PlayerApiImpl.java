/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Grants items as returned items of order owners, which {@code /order collect} hands out.
 */
final class PlayerApiImpl implements PlayerApi {
    private final AOrderData orderData;
    private final WriteAccess writes;
    private final Function<UUID, OfflinePlayer> players;
    private final Consumer<Event> events;
    private final Executor executor;

    PlayerApiImpl(
            AOrderData orderData,
            WriteAccess writes,
            Function<UUID, OfflinePlayer> players,
            Consumer<Event> events,
            Executor executor) {
        this.orderData = orderData;
        this.writes = writes;
        this.players = players;
        this.events = events;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> grantItems(UUID player, ItemStack stack, int amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stack, "stack");
        if (stack.getType().isAir()) throw new IllegalArgumentException("The stack must not be air");
        if (amount < 1) throw new IllegalArgumentException("The amount must be at least 1, was " + amount);
        var item = stack.asOne();
        return writes.run(services -> CompletableFuture.runAsync(() -> grant(player, item, amount), executor));
    }

    private void grant(UUID player, ItemStack item, int amount) {
        var stored = orderData
                .submitItemReturn(player, ReturnKind.OWNER, item, amount)
                .asFuture()
                .join();
        if (!Boolean.TRUE.equals(stored)) throw new IllegalStateException("The items could not be stored");
        events.accept(new ItemsReturnedEvent(
                players.apply(player),
                List.of(new ItemsReturnedEvent.ReturnedStack(item, amount)),
                ItemsReturnedEvent.Reason.API,
                null));
    }

    @Override
    public CompletableFuture<Integer> itemsWaiting(UUID player) {
        return CompletableFuture.supplyAsync(
                () -> orderData
                        .retrieveItemReturnCount(player, ReturnKind.OWNER)
                        .asFuture()
                        .join(),
                executor);
    }
}
