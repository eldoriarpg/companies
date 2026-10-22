/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.ItemMaterials;
import de.eldoria.companies.commands.suggest.SearchMaterials;
import de.eldoria.companies.commands.suggest.SortingTypes;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.util.Menus;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /company order search}: builds a search query, runs it and pages through the orders found.
 * <p>
 * Every change of the query shows the query again. Every page shown runs the search again, so the results are never
 * stale. Without a subcommand it shows the query.
 */
@Subcommand("search")
@PlayerOnly
public final class CompanyOrderSearchCommand {
    private static final int PAGE_SIZE = 10;
    private final CommandDependencies deps;
    private final SearchSessions sessions;
    private final SearchExecutor executor;

    /**
     * @param deps what the commands need
     */
    public CompanyOrderSearchCommand(CommandDependencies deps) {
        this.deps = deps;
        this.sessions = deps.searches();
        this.executor =
                new SearchExecutor(deps.orderData(), deps.companyData(), deps.configuration(), () -> deps.services()
                        .deliveryService()
                        .matcher());
    }

    /**
     * Shows the search query of the sender.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("query")
    @Executes("query render")
    public void render(CommandSender sender) {
        renderSearch(Senders.player(sender));
    }

    /**
     * Runs the search again and shows a page of the results.
     *
     * @param sender sender of the command
     * @param page   page to show, from zero
     */
    @Executes("page")
    public void page(CommandSender sender, @IntArg(min = 0) int page) {
        var player = Senders.player(sender);
        deps.requireReady();
        showFresh(player, page);
    }

    /**
     * Resets the search query.
     *
     * @param sender sender of the command
     */
    @Executes("query clear")
    public void clear(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        sessions.reset(player.getUniqueId());
        renderSearch(player);
    }

    /**
     * Runs the search and shows the first page of the results.
     *
     * @param sender sender of the command
     */
    @Executes("query execute")
    public void execute(CommandSender sender) {
        var player = Senders.player(sender);
        playerSearch(player);
        showFresh(player, 0);
    }

    /**
     * Shows only orders the sender can deliver items to, or every order.
     *
     * @param sender   sender of the command
     * @param fillable whether to filter by the items of the sender
     */
    @Executes("query fillable")
    public void fillable(CommandSender sender, boolean fillable) {
        change(sender, query -> query.fillable(fillable));
    }

    /**
     * Adds a material to search for.
     *
     * @param sender   sender of the command
     * @param material material name or part of it
     */
    @Executes("query material_add")
    public void addMaterial(CommandSender sender, @ItemMaterials String material) {
        change(sender, query -> query.materials().add(material));
    }

    /**
     * Removes every material from the search.
     *
     * @param sender sender of the command
     */
    @Executes("query material_remove")
    public void clearMaterials(CommandSender sender) {
        change(sender, query -> query.materials().clear());
    }

    /**
     * Removes a material from the search.
     *
     * @param sender   sender of the command
     * @param material material to remove
     */
    @Executes("query material_remove")
    public void removeMaterial(CommandSender sender, @SearchMaterials String material) {
        change(sender, query -> query.materials().remove(material));
    }

    /**
     * Matches materials by their whole name or by a part of it.
     *
     * @param sender sender of the command
     * @param mode   {@code exact} or {@code part}
     */
    @Executes("query material_match")
    public void materialMatch(CommandSender sender, @Literal({"exact", "part"}) String mode) {
        change(sender, query -> query.exactMatch("exact".equals(mode)));
    }

    /**
     * Finds orders with any or with all of the materials.
     *
     * @param sender sender of the command
     * @param mode   {@code any} or {@code all}
     */
    @Executes("query material_search")
    public void materialSearch(CommandSender sender, @Literal({"any", "all"}) String mode) {
        change(sender, query -> query.anyMaterial("any".equals(mode)));
    }

    /**
     * Clears the name to search for.
     *
     * @param sender sender of the command
     */
    @Executes("query name")
    public void clearName(CommandSender sender) {
        change(sender, query -> query.name(null));
    }

    /**
     * Sets the name to search for.
     *
     * @param sender sender of the command
     * @param name   order name or part of it
     */
    @Executes("query name")
    public void name(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        change(sender, query -> query.name(name));
    }

    /**
     * Sorts the results ascending or descending.
     *
     * @param sender    sender of the command
     * @param direction {@code asc} or {@code desc}
     */
    @Executes("query order")
    public void order(CommandSender sender, @Literal({"asc", "desc"}) String direction) {
        change(sender, query -> query.asc("asc".equals(direction)));
    }

    /**
     * Sets the lowest order price.
     *
     * @param sender sender of the command
     * @param price  lowest price
     */
    @Executes("query min_price")
    public void minPrice(CommandSender sender, @DoubleArg(min = 0) double price) {
        change(sender, query -> query.minPrice(price));
    }

    /**
     * Sets the highest order price.
     *
     * @param sender sender of the command
     * @param price  highest price
     */
    @Executes("query max_price")
    public void maxPrice(CommandSender sender, @DoubleArg(min = 0) double price) {
        change(sender, query -> query.maxPrice(price));
    }

    /**
     * Sets the smallest order size.
     *
     * @param sender sender of the command
     * @param size   smallest number of items
     */
    @Executes("query min_size")
    public void minSize(CommandSender sender, @IntArg(min = 0) int size) {
        change(sender, query -> query.minOrderSize(size));
    }

    /**
     * Sets the largest order size.
     *
     * @param sender sender of the command
     * @param size   largest number of items
     */
    @Executes("query max_size")
    public void maxSize(CommandSender sender, @IntArg(min = 0) int size) {
        change(sender, query -> query.maxOrderSize(size));
    }

    /**
     * Sets what the results are sorted by.
     *
     * @param sender sender of the command
     * @param type   name of the sorting
     */
    @Executes("query sorting")
    public void sorting(CommandSender sender, @SortingTypes String type) {
        var sorting = Checks.enumValue(type, SortingType.class);
        change(sender, query -> query.sortingType(sorting));
    }

    private void change(CommandSender sender, Consumer<SearchQuery> change) {
        var player = Senders.player(sender);
        change.accept(playerSearch(player));
        renderSearch(player);
    }

    /**
     * The search query of a player, created when missing. Holds the chat of the player, since a search menu follows.
     *
     * @param player player searching
     * @return the query
     */
    private SearchQuery playerSearch(Player player) {
        deps.services().messageBlocker().blockPlayer(player);
        return sessions.query(player.getUniqueId());
    }

    private void renderSearch(Player player) {
        var messageBlocker = deps.services().messageBlocker();
        var playerSearch = playerSearch(player);
        var message = MessageComposer.create().text(playerSearch.asComponent());
        if (messageBlocker.isBlocked(player)) {
            message.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        message.prependLines(25);
        Menus.show(messageBlocker, player, message);
    }

    /**
     * Runs the player's current search again and shows the given page of the fresh results. Has to be called on the
     * main thread.
     *
     * @param player player searching
     * @param page   page to show
     */
    private void showFresh(Player player, int page) {
        var optSearch = sessions.findQuery(player.getUniqueId());
        if (optSearch.isEmpty()) {
            renderPage(player, page);
            return;
        }
        executor.execute(player, optSearch.get()).whenComplete(found -> {
            sessions.results(player.getUniqueId(), found);
            renderPage(player, page);
        });
    }

    private void renderPage(Player player, int requestedPage) {
        var services = deps.services();
        var messageBlocker = services.messageBlocker();
        messageBlocker.blockPlayer(player);
        var results = sessions.results(player.getUniqueId());
        var fullOrders = results.orders();
        var page = Math.min(requestedPage, Math.max(0, (fullOrders.size() - 1) / PAGE_SIZE));

        var builder = MessageComposer.create()
                .text("<heading>")
                .localeCode("words.results")
                .text(": <value>%s", fullOrders.size())
                .space()
                .text("<click:run_command:/company order search query render><modify>[")
                .localeCode("words.change")
                .text("]</click>")
                .newLine();

        List<String> components = new ArrayList<>();
        for (var order : page(fullOrders, page)) {
            components.add(order.companyShortInfo(services.economy(), results.taxPercent(), results.canAccept()));
        }
        if (results.limitReached()) {
            components.add(MessageComposer.create()
                    .text("<inactive>")
                    .localeCode("company.order.search.limitReached")
                    .build());
        }

        builder.text(components).newLine();
        if (page != 0) {
            builder.text(
                    "<click:run_command:/company order search page %s> <active>%s </click>",
                    page - 1, Texts.LEFT_ARROW);
        } else {
            builder.text(" <inactive>%s ", Texts.LEFT_ARROW);
        }

        builder.text("<heading>%s/%s", page + 1, Math.max(1, (fullOrders.size() + PAGE_SIZE - 1) / PAGE_SIZE));

        if (!page(fullOrders, page + 1).isEmpty()) {
            builder.text(
                    "<click:run_command:/company order search page %s> <active>%s </click>",
                    page + 1, Texts.RIGHT_ARROW);
        } else {
            builder.text("<inactive> %s ", Texts.RIGHT_ARROW);
        }
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        builder.prependLines(25);
        Menus.show(messageBlocker, player, builder);
    }

    private static List<FullOrder> page(List<FullOrder> orders, int page) {
        if (page < 0) return Collections.emptyList();
        var start = page * PAGE_SIZE;
        if (start >= orders.size()) return Collections.emptyList();
        return orders.subList(start, Math.min(start + PAGE_SIZE, orders.size()));
    }
}
