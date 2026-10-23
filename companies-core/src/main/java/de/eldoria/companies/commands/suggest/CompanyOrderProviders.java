/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.commands.company.order.search.SortingType;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of {@code /company order}.
 * <p>
 * Orders are anonymous: tooltips show the order name, the items and amounts, never the owner.
 */
public final class CompanyOrderProviders {
    private static final List<String> ITEM_MATERIALS = Arrays.stream(Material.values())
            .filter(material -> !material.isLegacy())
            .filter(Material::isItem)
            .filter(material -> !material.isAir())
            .map(material -> material.name().toLowerCase(Locale.ROOT))
            .toList();

    private CompanyOrderProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return ids of the orders the sender's company works on, starting with what is typed
     */
    @CompanyOrderIds
    public static CompletableFuture<Suggestions> companyOrderIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return CompanyOrderSources.current()
                .map(sources -> sources.companyOrders()
                        .suggest(player.getUniqueId(), builder, CompanyOrderProviders::suggestOrders))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return lines of the order in argument {@code id} that still miss items, starting with what is typed
     */
    @OrderLines
    public static CompletableFuture<Suggestions> orderLines(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        var id = orderId(context);
        if (id.isEmpty()) return builder.buildFuture();
        return CompanyOrderSources.current()
                .map(sources -> sources.companyOrders()
                        .suggest(player.getUniqueId(), builder, (orders, target) -> order(orders, id.get())
                                .ifPresent(order -> suggestLines(order, target))))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return ids of the orders the sender works on alone, starting with what is typed
     */
    @SoloOrderIds
    public static CompletableFuture<Suggestions> soloOrderIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return CompanyOrderSources.current()
                .map(sources -> sources.soloOrders()
                        .suggest(player.getUniqueId(), builder, CompanyOrderProviders::suggestOrders))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return lines of the solo order in argument {@code id} that still miss items, starting with what is typed
     */
    @SoloOrderLines
    public static CompletableFuture<Suggestions> soloOrderLines(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        var id = orderId(context);
        if (id.isEmpty()) return builder.buildFuture();
        return CompanyOrderSources.current()
                .map(sources -> sources.soloOrders()
                        .suggest(player.getUniqueId(), builder, (orders, target) -> order(orders, id.get())
                                .ifPresent(order -> suggestLines(order, target))))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return ids of the orders the sender's last search found, starting with what is typed
     */
    @SearchResultIds
    public static CompletableFuture<Suggestions> searchResultIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        CompanyOrderSources.current()
                .ifPresent(sources -> suggestOrders(
                        sources.searches().results(player.getUniqueId()).orders(), builder));
        return builder.buildFuture();
    }

    /**
     * @return item materials starting with what is typed
     */
    @ItemMaterials
    public static CompletableFuture<Suggestions> itemMaterials(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(ITEM_MATERIALS, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the materials of the sender's search query starting with what is typed
     */
    @SearchMaterials
    public static CompletableFuture<Suggestions> searchMaterials(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        CompanyOrderSources.current()
                .flatMap(sources -> sources.searches().findQuery(player.getUniqueId()))
                .ifPresent(query -> CompanyProviders.matching(query.materials(), builder.getRemaining())
                        .forEach(builder::suggest));
        return builder.buildFuture();
    }

    /**
     * @return the sortings of the search starting with what is typed
     */
    @SortingTypes
    public static CompletableFuture<Suggestions> sortingTypes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(sortingNames(), builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the names of the sortings in lower case
     */
    public static List<String> sortingNames() {
        return Arrays.stream(SortingType.values())
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * @param orders orders to offer
     * @param typed  what is typed so far
     * @return the orders whose id starts with what is typed
     */
    public static List<FullOrder> matchingOrders(Collection<FullOrder> orders, String typed) {
        return orders.stream()
                .filter(order -> String.valueOf(order.id()).startsWith(typed))
                .toList();
    }

    /**
     * The lines a delivery can still go to, numbered from one as players type them.
     *
     * @param order order to deliver to
     * @param typed what is typed so far
     * @return the line numbers that start with what is typed and still miss items
     */
    public static List<Integer> missingLines(FullOrder order, String typed) {
        return order.contents().stream()
                .filter(content -> content.missing() > 0)
                .map(content -> content.line() + 1)
                .filter(line -> String.valueOf(line).startsWith(typed))
                .toList();
    }

    /**
     * @param orders orders to search
     * @param id     id of the order
     * @return the order with the id
     */
    public static Optional<FullOrder> order(Collection<FullOrder> orders, int id) {
        return orders.stream().filter(order -> order.id() == id).findFirst();
    }

    private static void suggestOrders(Collection<FullOrder> orders, SuggestionsBuilder builder) {
        for (var order : matchingOrders(orders, builder.getRemaining())) {
            builder.suggest(order.id(), tooltip(Component.text(order.name())));
        }
    }

    private static void suggestLines(FullOrder order, SuggestionsBuilder builder) {
        for (var line : missingLines(order, builder.getRemaining())) {
            order.line(line - 1)
                    .ifPresent(content -> builder.suggest(
                            line, tooltip(content.itemName().append(Component.text(" ×" + content.missing())))));
        }
    }

    private static com.mojang.brigadier.Message tooltip(Component component) {
        return MessageComponentSerializer.message().serialize(component);
    }

    private static Optional<Integer> orderId(CommandContext<CommandSourceStack> context) {
        try {
            return Optional.of(context.getArgument("id", Integer.class));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
