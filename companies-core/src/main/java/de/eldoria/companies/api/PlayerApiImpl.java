/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Grants items as returned items of order owners, which {@code /order collect} hands out. The item returns store them
 * and fire {@link ItemsReturnedEvent} with {@link ItemsReturnedEvent.Reason#API}.
 */
final class PlayerApiImpl implements PlayerApi {
    private final AOrderData orderData;
    private final WriteAccess writes;
    private final Executor executor;

    PlayerApiImpl(AOrderData orderData, WriteAccess writes, Executor executor) {
        this.orderData = orderData;
        this.writes = writes;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> grantItems(UUID player, ItemStack stack, int amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stack, "stack");
        if (stack.getType().isAir()) throw new IllegalArgumentException("The stack must not be air");
        if (amount < 1) throw new IllegalArgumentException("The amount must be at least 1, was " + amount);
        var item = stack.asOne();
        return writes.run(services -> services.itemReturns()
                .store(player, ReturnKind.OWNER, item, amount, ItemsReturnedEvent.Reason.API, null)
                .thenAcceptAsync(
                        stored -> {
                            if (!Boolean.TRUE.equals(stored)) {
                                throw new IllegalStateException("The items could not be stored");
                            }
                        },
                        executor));
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
