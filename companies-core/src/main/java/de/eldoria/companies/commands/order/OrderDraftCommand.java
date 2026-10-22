/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.DraftLines;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.suggest.UnitPrices;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.util.Menus;
import de.eldoria.companies.util.Permission.Orders;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
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
 * Every action shows the builder again, also when it was refused. An action on a draft that expired answers that the
 * draft expired instead of starting a new one. Lines are numbered from 1, prices are per unit.
 */
@Subcommand("draft")
@Permission(Orders.CREATE)
@PlayerOnly
public final class OrderDraftCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public OrderDraftCommand(CommandDependencies deps) {
        this.deps = deps;
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
        var player = Senders.player(sender);
        var services = deps.services();
        var blocker = services.messageBlocker();
        var economy = services.economy();
        blocker.unblockPlayer(player);
        drafts().done(player, economy).whenComplete((created, err) -> {
            if (err != null) {
                blocker.unblockPlayer(player).thenRun(() -> report(player, err));
                return;
            }
            SuggestionSources.current().ifPresent(sources -> sources.ownOrders().invalidate(player.getUniqueId()));
            blocker.unblockPlayer(player).whenComplete((unused, error) -> sendCreated(player, created, economy));
        });
    }

    /**
     * Drops the sender's draft.
     *
     * @param sender sender of the command
     */
    @Executes("cancel")
    public void cancel(CommandSender sender) {
        var player = Senders.player(sender);
        var blocker = deps.services().messageBlocker();
        if (drafts().draft(player.getUniqueId()).isEmpty()) {
            expired(player, blocker);
            return;
        }
        drafts().cancel(player.getUniqueId());
        blocker.unblockPlayer(player).thenRun(() -> deps.messages().sendMessage(player, "words.aborted"));
    }

    /**
     * Shows the builder of a draft past the message blocker.
     *
     * @param player owner of the draft
     * @param draft  the draft
     */
    public void show(Player player, OrderBuilder draft) {
        var services = deps.services();
        var blocker = services.messageBlocker();
        var composer = draft.asComponent(deps.configuration().orderSetting(), services.economy(), deps.orderData());
        if (blocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        composer.prependLines(25);
        Menus.show(blocker, player, composer);
    }

    private void edit(CommandSender sender, BiConsumer<Player, OrderBuilder> action) {
        var player = Senders.player(sender);
        var blocker = deps.services().messageBlocker();
        var draft = drafts().draft(player.getUniqueId());
        if (draft.isEmpty()) {
            expired(player, blocker);
            return;
        }
        blocker.blockPlayer(player);
        try {
            action.accept(player, draft.get());
        } finally {
            show(player, draft.get());
        }
    }

    private void expired(Player player, MessageBlocker blocker) {
        blocker.unblockPlayer(player).thenRun(() -> deps.messages().sendError(player, "order.create.draftExpired"));
    }

    /**
     * Reports why a draft action failed: the error of a {@link CommandFailure}, otherwise a failed save.
     *
     * @param sender sender of the action
     * @param err    the failure, possibly wrapped by a future
     */
    void report(CommandSender sender, Throwable err) {
        var cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        if (cause instanceof CommandFailure failure) {
            failure.sendTo(sender, deps.messages());
            return;
        }
        deps.plugin().getLogger().log(Level.SEVERE, "An order draft action failed", cause);
        deps.messages().sendError(sender, "error.saveFailed");
    }

    private void sendCreated(Player player, OrderDraftService.Created created, Economy economy) {
        if (created.fee() > 0) {
            deps.messages()
                    .sendMessage(
                            player,
                            "order.create.createdWithFee",
                            Replacement.create("price", economy.format(created.price())),
                            Replacement.create("fee", economy.format(created.fee())),
                            Replacement.create("total", economy.format(created.price() + created.fee())));
            return;
        }
        deps.messages().sendMessage(player, MessageComposer.escape("order.create.created"));
    }

    private OrderDraftService drafts() {
        return deps.drafts();
    }
}
