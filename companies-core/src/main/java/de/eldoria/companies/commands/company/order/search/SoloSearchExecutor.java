/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.Deliverable;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.SoloOrderService;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs a search for a player who wants to work alone and keeps only the orders the player could take.
 * <p>
 * Hidden are the player's own orders, orders larger than solo orders may be and, with the fillable filter, orders the
 * player can deliver nothing to. A player at the solo limit gets no orders. A player who may not work alone sees the
 * orders but cannot accept them.
 */
public final class SoloSearchExecutor implements OrderFinder {
    private final AOrderData orderData;
    private final Supplier<SoloOrderService> soloOrders;
    private final Supplier<ItemMatcher> matcher;

    /**
     * @param orderData  order repository
     * @param soloOrders the solo order service, which knows whether the player may work alone
     * @param matcher    the current item matcher, which decides what the player can deliver
     */
    public SoloSearchExecutor(
            AOrderData orderData, Supplier<SoloOrderService> soloOrders, Supplier<ItemMatcher> matcher) {
        this.orderData = orderData;
        this.soloOrders = soloOrders;
        this.matcher = matcher;
    }

    @Override
    public BukkitFutureResult<SearchResults> execute(Player player, SearchQuery query) {
        List<ItemStack> available = query.fillable() ? Deliverable.available(player) : List.of();
        var itemMatcher = matcher.get();
        var service = soloOrders.get();
        var orders = orderData
                .retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .asFuture();
        return BukkitFutureResult.of(service.eligibility(player)
                .thenCombineAsync(
                        orders,
                        (refusal, found) -> results(
                                player, service, refusal, SearchExecutor.filled(found, query, itemMatcher, available)),
                        Threading.VIRTUAL));
    }

    private SearchResults results(
            Player player, SoloOrderService service, Optional<CommandFailure> refusal, List<FullOrder> orders) {
        var solo = service.settings();
        var canAccept = refusal.isEmpty();
        var active = orderData.retrieveSoloOrderCount(player.getUniqueId()).join();
        if (canAccept && active != null && active >= solo.maxActive()) {
            return new SearchResults(List.of(), solo.taxPercent(), true, true);
        }
        var takeable = orders.stream()
                .filter(order -> !order.owner().equals(player.getUniqueId()))
                .filter(order -> !solo.exceedsOrderSize(order.amount()))
                .toList();
        return new SearchResults(takeable, solo.taxPercent(), canAccept, false);
    }
}
