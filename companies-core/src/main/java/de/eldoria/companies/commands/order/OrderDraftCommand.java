/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.DraftLines;
import de.eldoria.companies.commands.suggest.UnitPrices;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.order.OrderBuilderController;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.util.Permission.Orders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;

/**
 * {@code /order draft …}: the actions of the order builder on the sender's draft.
 * <p>
 * The typed counterpart of the builder's buttons. Every action shows the builder again, also when it was refused. An
 * action on a draft that expired answers that the draft expired instead of starting a new one. Lines are numbered
 * from 1, prices are per unit.
 */
@Subcommand("draft")
@Permission(Orders.CREATE)
@PlayerOnly
public final class OrderDraftCommand {
    private final CommandDependencies deps;
    private final OrderBuilderController builder;

    /**
     * @param deps what the commands need
     */
    public OrderDraftCommand(CommandDependencies deps) {
        this.deps = deps;
        this.builder = new OrderBuilderController(deps);
    }

    /**
     * @param sender sender of the command
     * @param name   new name of the order
     */
    @Executes("name")
    public void name(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        edit(sender, (player, draft) -> drafts().rename(draft, name));
    }

    /**
     * Adds an item, typed with its components when an exact item is wanted.
     *
     * @param sender    sender of the command
     * @param item      requested item
     * @param amount    number of items
     * @param unitPrice price of one item
     */
    @Executes("add")
    public void add(
            CommandSender sender,
            ItemStack item,
            @IntArg(min = 1) int amount,
            @UnitPrices @DoubleArg(min = 0, max = Double.MAX_VALUE) double unitPrice) {
        edit(sender, (player, draft) -> drafts().add(draft, item, amount, unitPrice));
    }

    /**
     * Adds the item in the sender's main hand. An item with data becomes an exact line.
     *
     * @param sender    sender of the command
     * @param amount    number of items
     * @param unitPrice price of one item
     */
    @Executes("add hand")
    public void addHand(
            CommandSender sender,
            @IntArg(min = 1) int amount,
            @UnitPrices @DoubleArg(min = 0, max = Double.MAX_VALUE) double unitPrice) {
        edit(sender, (player, draft) -> drafts().addHand(draft, player, amount, unitPrice));
    }

    /**
     * @param sender sender of the command
     * @param line   number of the line
     */
    @Executes("remove")
    public void remove(CommandSender sender, @DraftLines @IntArg(min = 1) int line) {
        edit(sender, (player, draft) -> drafts().remove(draft, line));
    }

    /**
     * Changes the amount of a line and keeps its unit price. An amount of zero removes the line.
     *
     * @param sender sender of the command
     * @param line   number of the line
     * @param amount new amount
     */
    @Executes("amount")
    public void amount(CommandSender sender, @DraftLines @IntArg(min = 1) int line, @IntArg(min = 0) int amount) {
        edit(sender, (player, draft) -> drafts().amount(draft, line, amount));
    }

    /**
     * @param sender    sender of the command
     * @param line      number of the line
     * @param unitPrice new price of one item
     */
    @Executes("price")
    public void price(
            CommandSender sender,
            @DraftLines @IntArg(min = 1) int line,
            @UnitPrices @DoubleArg(min = 0, max = Double.MAX_VALUE) double unitPrice) {
        edit(sender, (player, draft) -> drafts().unitPrice(draft, line, unitPrice));
    }

    /**
     * Sets the unit price of a material line to the market average.
     *
     * @param sender sender of the command
     * @param line   number of the line
     * @param market the literal {@code market}
     */
    @Executes("price")
    public void marketPrice(
            CommandSender sender, @DraftLines @IntArg(min = 1) int line, @Literal("market") String market) {
        edit(sender, (player, draft) -> drafts().marketPrice(draft, line));
    }

    /**
     * Charges the sender and creates the order.
     *
     * @param sender sender of the command
     */
    @Executes("done")
    public void done(CommandSender sender) {
        builder.done(Senders.player(sender));
    }

    /**
     * Drops the sender's draft.
     *
     * @param sender sender of the command
     */
    @Executes("cancel")
    public void cancel(CommandSender sender) {
        builder.cancel(Senders.player(sender));
    }

    /**
     * Shows the builder of a draft.
     *
     * @param player owner of the draft
     * @param draft  the draft
     */
    public void show(Player player, OrderBuilder draft) {
        builder.show(player, draft);
    }

    /**
     * Reports why a draft action failed.
     *
     * @param sender sender of the action
     * @param err    the failure, possibly wrapped by a future
     */
    void report(CommandSender sender, Throwable err) {
        builder.report(sender, err);
    }

    private void edit(CommandSender sender, BiConsumer<Player, OrderBuilder> action) {
        builder.edit(Senders.player(sender), action);
    }

    private OrderDraftService drafts() {
        return deps.drafts();
    }
}
