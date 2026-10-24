/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.DraftLines;
import de.eldoria.companies.commands.suggest.SavedItemNames;
import de.eldoria.companies.commands.suggest.UnitPrices;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.saved.SavedItem;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.menus.order.OrderBuilderController;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.Repetition;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.Permission.Orders;
import de.eldoria.companies.util.Threading;
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

import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * {@code /order draft …}: the actions of the order builder on the sender's draft.
 * <p>
 * The typed counterpart of the builder's buttons. Every action shows the builder again, also when it was refused. An
 * action on a draft that expired answers that the draft expired instead of starting a new one. Lines are numbered
 * from 1, prices are per unit. {@code repeat} makes the order repeat daily or weekly, until a number of copies or a
 * date, and {@code repeat off} makes it a one-off order again.
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
     * Adds a saved item. Its data decides whether it becomes an exact line, as for the item in hand. A refusal, such as
     * a full draft, is told to the player, who sees the draft unchanged.
     *
     * @param sender    sender of the command
     * @param name      name of the saved item
     * @param amount    number of items
     * @param unitPrice price of one item
     */
    @Executes("add saved")
    public void addSaved(
            CommandSender sender,
            @SavedItemNames @StringArg(StringArgType.WORD) String name,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = Double.MAX_VALUE) double unitPrice) {
        var player = Senders.player(sender);
        deps.services()
                .savedItems()
                .item(player.getUniqueId(), name)
                .thenAcceptAsync(
                        result -> {
                            switch (result) {
                                case ActionResult.Refused<SavedItem> refused -> throw refused.failure();
                                case ActionResult.Done<SavedItem>(var saved) ->
                                    edit(player, (owner, draft) -> drafts().add(
                                                    draft, saved.stack(), amount, unitPrice));
                            }
                        },
                        Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    var cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                    if (cause instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not add a saved item to a draft", cause);
                    deps.messages().sendError(player, "error.saveFailed");
                    return null;
                });
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
     * Makes the order repeat until a number of copies was posted.
     *
     * @param sender   sender of the command
     * @param interval {@code daily} or {@code weekly}
     * @param count    number of copies
     */
    @Executes("repeat")
    public void repeat(
            CommandSender sender, @Literal({"daily", "weekly"}) String interval, @IntArg(min = 1) int count) {
        edit(sender, (player, draft) -> drafts().repeat(draft, player, Repetition.times(interval(interval), count)));
    }

    /**
     * Makes the order repeat until a date.
     *
     * @param sender   sender of the command
     * @param interval {@code daily} or {@code weekly}
     * @param until    the literal {@code until}
     * @param date     the last day a copy may be posted, as {@code yyyy-MM-dd}
     */
    @Executes("repeat")
    public void repeatUntil(
            CommandSender sender,
            @Literal({"daily", "weekly"}) String interval,
            @Literal("until") String until,
            String date) {
        edit(sender, (player, draft) -> {
            var end =
                    Repetition.date(date).orElseThrow(() -> CommandFailure.actionBar("order.draft.repeat.error.date"));
            drafts().repeat(draft, player, Repetition.until(interval(interval), end));
        });
    }

    /**
     * Makes the order a one-off order again.
     *
     * @param sender sender of the command
     */
    @Executes("repeat off")
    public void once(CommandSender sender) {
        edit(sender, (player, draft) -> drafts().once(draft));
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

    private static RepeatInterval interval(String typed) {
        return Repetition.interval(typed).orElseThrow(() -> CommandFailure.actionBar("error.invalidEnumValue"));
    }

    private OrderDraftService drafts() {
        return deps.drafts();
    }
}
