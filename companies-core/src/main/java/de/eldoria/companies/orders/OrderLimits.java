/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * Decides how many active orders a player may have at once.
 * <p>
 * A {@code companies.orders.limit.<n>} permission overrides the configured {@code maxOrders}. Only orders which are not
 * delivered yet count towards the limit.
 */
public class OrderLimits {
    private final AOrderData orderData;
    private final Supplier<UserSettings> userSettings;

    /**
     * Creates the limits of the order owners.
     *
     * @param orderData    order data counting the active orders
     * @param userSettings current user settings, read on every check so a reload applies at once
     */
    public OrderLimits(AOrderData orderData, Supplier<UserSettings> userSettings) {
        this.orderData = orderData;
        this.userSettings = userSettings;
    }

    /**
     * The maximum number of active orders of a player.
     *
     * @param player player to check
     * @return the highest limit permission of the player, otherwise the configured limit
     */
    public int limit(Player player) {
        return Permission.Orders.getOrderOverride(player)
                .orElse(userSettings.get().maxOrders());
    }

    /**
     * The maximum number of active orders of a player who may be offline. Permissions are only known while the player
     * is online.
     *
     * @param player player to check
     * @return the limit of the online player, otherwise the configured limit
     */
    public int limit(OfflinePlayer player) {
        var online = player instanceof Player present ? present : player.getPlayer();
        if (online != null) return limit(online);
        return userSettings.get().maxOrders();
    }

    /**
     * Counts the active orders of a player and pairs them with the player's limit.
     * <p>
     * The limit is read on the calling thread, the count is retrieved asynchronously.
     *
     * @param player player to check
     * @return a future providing the usage of the player
     */
    public BukkitFutureResult<Usage> usage(OfflinePlayer player) {
        var limit = limit(player);
        return BukkitFutureResult.of(
                orderData.retrievePlayerOrderCount(player).asFuture().thenApply(active -> new Usage(active, limit)));
    }

    /**
     * The active orders of a player against the player's limit.
     *
     * @param active number of orders which are not delivered yet
     * @param limit  maximum number of active orders
     */
    public record Usage(int active, int limit) {
        /**
         * Whether the player may not create another order.
         *
         * @return true when the active orders reached the limit
         */
        public boolean reached() {
            return active >= limit;
        }
    }
}
