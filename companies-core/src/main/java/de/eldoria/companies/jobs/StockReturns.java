/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.services.ItemReturnService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moves items of a job's stock into a player's item returns, where they are collected with {@code /order returns}.
 */
public final class StockReturns {
    private StockReturns() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param returns the item returns
     * @param logger  reports items that could not be stored
     * @return stores items, as amounts by material name, for a player and answers the ones it could not store
     */
    public static BiFunction<UUID, Map<String, Integer>, Map<String, Integer>> of(
            ItemReturnService returns, Logger logger) {
        return (player, items) -> store(returns, logger, player, items);
    }

    private static Map<String, Integer> store(
            ItemReturnService returns, Logger logger, UUID player, Map<String, Integer> items) {
        Map<String, Integer> left = new LinkedHashMap<>();
        items.forEach((name, amount) -> {
            var stored = false;
            try {
                var type = Material.matchMaterial(name);
                stored = type != null
                        && type.isItem()
                        && Boolean.TRUE.equals(returns.store(
                                        player,
                                        ReturnKind.OWNER,
                                        new ItemStack(type),
                                        amount,
                                        ItemsReturnedEvent.Reason.JOB_STOCK,
                                        null)
                                .join());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not store " + amount + " " + name + " for " + player, e);
            }
            if (!stored) left.put(name, amount);
        });
        return left;
    }
}
