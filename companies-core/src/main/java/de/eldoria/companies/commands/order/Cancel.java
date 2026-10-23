/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.suggest.SeriesSources;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /order cancel <id>}: cancels an unaccepted order of the sender. The price is refunded, the fee goes to the
 * collector.
 * <p>
 * Cancelling a copy of an active repeating order stops the repeating order as well. The sender confirms that first,
 * with {@code /order cancel confirm} or the buttons of the confirmation.
 */
@Subcommand("cancel")
@PlayerOnly
public final class Cancel {
    private final CommandDependencies deps;
    private final OwnerOrderViews views;
    private final ConfirmFlow<Integer> copies;

    /**
     * @param deps  what the commands need
     * @param views shows the orders once the order is canceled
     */
    public Cancel(CommandDependencies deps, OwnerOrderViews views) {
        this.deps = deps;
        this.views = views;
        this.copies = ConfirmFlow.create(deps.plugin(), this::copyConfirmed);
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void cancel(CommandSender sender, @OwnOpenOrderIds int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        load(player, id)
                .thenAccept(order -> order.ifPresent(open -> confirmOrCancel(player, open)))
                .exceptionally(this::log);
    }

    /**
     * Cancels the copy of a repeating order the sender confirmed, which stops the repeating order.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        copies.confirm(player);
    }

    private void confirmOrCancel(Player player, SimpleOrder order) {
        var series = order.seriesId();
        if (series.isEmpty()) {
            cancelNow(player, order);
            return;
        }
        deps.economy()
                .series()
                .retrieveSeries(series.getAsInt())
                .thenAccept(found -> {
                    var active = found.filter(candidate -> candidate.state() == SeriesState.ACTIVE);
                    if (active.isEmpty()) {
                        cancelNow(player, order);
                        return;
                    }
                    var question = MenuText.create()
                            .locale(
                                    "order.cancel.stopsSeries",
                                    Values.text("order_name", order.fullName()),
                                    Values.text("series_name", active.get().name()));
                    copies.ask(deps.services().menus(), player, order.id(), question);
                })
                .exceptionally(this::log);
    }

    private void copyConfirmed(Player player, int id) {
        load(player, id)
                .thenAccept(order -> order.ifPresent(open -> cancelNow(player, open)))
                .exceptionally(this::log);
    }

    /**
     * Loads an unaccepted order of the player and reports why it can not be cancelled otherwise.
     */
    private CompletableFuture<Optional<SimpleOrder>> load(Player player, int id) {
        var messages = deps.messages();
        return deps.orderData()
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenApply(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return Optional.empty();
                    }
                    var simpleOrder = optOrder.get();
                    if (!simpleOrder.owner().equals(player.getUniqueId())) {
                        messages.sendError(player, "error.notYourOrder");
                        return Optional.empty();
                    }
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messages.sendError(player, "error.orderAlreadyClaimed");
                        return Optional.empty();
                    }
                    return Optional.of(simpleOrder);
                });
    }

    private void cancelNow(Player player, SimpleOrder simpleOrder) {
        var economy = deps.services().economy();
        var collector = new Collector(deps.plugin(), economy, deps.configuration());
        var orderData = deps.orderData();
        var messages = deps.messages();
        CompletableFuture.runAsync(
                        () -> {
                            var fullOrder =
                                    orderData.retrieveFullOrder(simpleOrder).join();
                            if (fullOrder == null) {
                                messages.sendErrorActionBar(player, "error.unknownOrder");
                                return;
                            }
                            if (!Boolean.TRUE.equals(
                                    deps.outcomes().cancel(fullOrder).join())) {
                                messages.sendError(player, "error.orderAlreadyClaimed");
                                return;
                            }
                            SuggestionSources.current()
                                    .ifPresent(sources -> sources.ownOrders().invalidate(player.getUniqueId()));
                            Threading.mainThread(deps.plugin())
                                    .execute(() -> economy.depositPlayer(player, fullOrder.price()));
                            collector.collect(fullOrder.fee());
                            stopSeries(player, fullOrder);
                            views.showOrders(player, () -> sendCanceled(player, fullOrder, economy));
                        },
                        Threading.VIRTUAL)
                .exceptionally(this::log);
    }

    private void stopSeries(Player player, FullOrder order) {
        if (order.seriesId().isEmpty()) return;
        deps.services().series().onCopyCancelled(order).join();
        SeriesSources.invalidate(player.getUniqueId());
    }

    private Void log(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }

    private void sendCanceled(Player player, FullOrder order, Economy economy) {
        var money = Replacement.create("money", economy.format(order.price()));
        if (order.fee() > 0) {
            deps.messages()
                    .sendMessage(
                            player,
                            "order.cancel.canceledFeeKept",
                            money,
                            Replacement.create("fee", economy.format(order.fee())));
            return;
        }
        deps.messages().sendMessage(player, "order.cancel.canceled", money);
    }
}
