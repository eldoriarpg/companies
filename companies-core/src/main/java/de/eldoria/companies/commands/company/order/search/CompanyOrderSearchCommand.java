/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.ItemMaterials;
import de.eldoria.companies.commands.suggest.SearchMaterials;
import de.eldoria.companies.commands.suggest.SortingTypes;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * {@code /company order search}: shows the search form, whose buttons run the search and page through the orders
 * found. {@code /order work search} is the same tree on the search of orders to work on alone.
 * <p>
 * The {@code query …} subcommands change the query by typing, for the values a chat menu cannot take, and show the
 * form again.
 */
@Subcommand("search")
@PlayerOnly
public final class CompanyOrderSearchCommand {
    private final OrderSearch search;

    /**
     * @param deps   what the commands need
     * @param info   shows a company order to a player
     * @param accept accepts an order for the company of a player
     */
    public CompanyOrderSearchCommand(
            CommandDependencies deps, ObjIntConsumer<Player> info, ObjIntConsumer<Player> accept) {
        this(new OrderSearch(deps, info, accept));
    }

    /**
     * @param search the search the commands act on, such as the search of orders to work on alone
     */
    public CompanyOrderSearchCommand(OrderSearch search) {
        this.search = search;
    }

    /**
     * Shows the search form.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("query")
    public void render(CommandSender sender) {
        search.showForm(Senders.player(sender));
    }

    /**
     * Resets the search query.
     *
     * @param sender sender of the command
     */
    @Executes("query clear")
    public void clear(CommandSender sender) {
        search.reset(Senders.player(sender));
    }

    /**
     * Runs the search and shows the first page of the results.
     *
     * @param sender sender of the command
     */
    @Executes("query execute")
    public void execute(CommandSender sender) {
        search.showPage(Senders.player(sender), 0);
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
        search.change(Senders.player(sender), change);
    }
}
