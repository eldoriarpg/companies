/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Provides the suggestions about the sender's own orders and order draft.
 */
public final class OrderProviders {
    private static final Set<OrderState> NOT_RECEIVED =
            EnumSet.of(OrderState.UNCLAIMED, OrderState.CLAIMED, OrderState.DELIVERED);

    private OrderProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return ids of the sender's orders which are not received yet, starting with what is typed
     */
    @OwnOrderIds
    public static CompletableFuture<Suggestions> ownOrderIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return ownOrderIds(context, builder, NOT_RECEIVED);
    }

    /**
     * @return ids of the sender's orders nobody accepted yet, starting with what is typed
     */
    @OwnOpenOrderIds
    public static CompletableFuture<Suggestions> ownOpenOrderIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return ownOrderIds(context, builder, EnumSet.of(OrderState.UNCLAIMED));
    }

    /**
     * @return ids of the sender's delivered orders, starting with what is typed
     */
    @OwnDeliveredOrderIds
    public static CompletableFuture<Suggestions> ownDeliveredOrderIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return ownOrderIds(context, builder, EnumSet.of(OrderState.DELIVERED));
    }

    /**
     * @return line numbers of the sender's draft starting with what is typed, with item and amount as tooltip
     */
    @DraftLines
    public static CompletableFuture<Suggestions> draftLines(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        var draft =
                SuggestionSources.current().flatMap(sources -> sources.drafts().draft(player.getUniqueId()));
        if (draft.isEmpty()) return builder.buildFuture();
        var lines = draft.get().lines();
        for (var number : lineNumbers(lines.size(), builder.getRemaining())) {
            var line = lines.get(number - 1);
            builder.suggest(
                    number,
                    MessageComponentSerializer.message()
                            .serialize(line.itemName().append(Component.text(" ×" + line.amount()))));
        }
        return builder.buildFuture();
    }

    /**
     * Suggests the market average unit price of the item the action is about: the draft line in argument
     * {@code line}, the item in argument {@code item}, or else the item in the sender's main hand. Exact items and
     * materials without market data get no suggestion.
     *
     * @return the market average as a number, with a tooltip
     */
    @UnitPrices
    public static CompletableFuture<Suggestions> unitPrices(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        var sources = SuggestionSources.current();
        if (sources.isEmpty()) return builder.buildFuture();
        var source = sources.get();
        requested(context, source.drafts(), player)
                .flatMap(item -> unitPrice(
                        item.exact(),
                        source.orderData()
                                .getMaterialPrice(
                                        item.template().getType().name().toLowerCase(Locale.ROOT))))
                .filter(price -> price.startsWith(builder.getRemaining()))
                .ifPresent(price -> builder.suggest(
                        price,
                        MessageComponentSerializer.message()
                                .serialize(Component.text(source.localize("order.draft.marketAverage")))));
        return builder.buildFuture();
    }

    /**
     * @param count number of lines of the draft
     * @param typed what is typed so far
     * @return the line numbers from 1 to the count whose digits start with what is typed
     */
    public static List<Integer> lineNumbers(int count, String typed) {
        return IntStream.rangeClosed(1, count)
                .filter(line -> String.valueOf(line).startsWith(typed))
                .boxed()
                .toList();
    }

    /**
     * @param orders orders of the player
     * @param states states to keep
     * @param typed  what is typed so far
     * @return the orders in one of the states whose id starts with what is typed
     */
    public static List<OwnOrder> ownOrders(List<OwnOrder> orders, Set<OrderState> states, String typed) {
        return orders.stream()
                .filter(order -> states.contains(order.state()))
                .filter(order -> String.valueOf(order.id()).startsWith(typed))
                .toList();
    }

    /**
     * The unit price suggested for an item: the market average, as long as the item is no exact item and the market
     * has data.
     *
     * @param exact whether the item is requested exactly
     * @param price market price of the item's material
     * @return the average unit price as it is typed, with at most two decimals
     */
    public static Optional<String> unitPrice(boolean exact, MaterialPrice price) {
        return OrderBuilder.offeredMarketPrice(exact, price).map(offered -> BigDecimal.valueOf(offered.avgPrice())
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString());
    }

    private static CompletableFuture<Suggestions> ownOrderIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, Set<OrderState> states) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return SuggestionSources.current()
                .map(sources -> sources.ownOrders()
                        .suggest(player.getUniqueId(), builder, (orders, target) -> ownOrders(
                                        orders, states, target.getRemaining())
                                .forEach(order -> target.suggest(
                                        order.id(),
                                        MessageComponentSerializer.message().serialize(order.tooltip())))))
                .orElseGet(builder::buildFuture);
    }

    private static Optional<OrderDraftService.RequestedItem> requested(
            CommandContext<CommandSourceStack> context, OrderDraftService drafts, Player player) {
        var line = argument(context, "line", Integer.class);
        if (line.isPresent()) {
            return drafts.draft(player.getUniqueId())
                    .flatMap(draft -> draft.line(line.get()))
                    .map(draftLine -> new OrderDraftService.RequestedItem(draftLine.template(), draftLine.exact()));
        }
        var item = argument(context, "item", ItemStack.class)
                .orElseGet(() -> player.getInventory().getItemInMainHand());
        if (item.isEmpty()) return Optional.empty();
        return drafts.request(item);
    }

    private static <T> Optional<T> argument(CommandContext<CommandSourceStack> context, String name, Class<T> type) {
        try {
            return Optional.of(context.getArgument(name, type));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
