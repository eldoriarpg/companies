/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.CompanyOrderSearchCommand;
import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.company.order.search.SearchMode;
import de.eldoria.companies.commands.company.order.search.SoloSearchExecutor;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.suggest.SearchResultIds;
import de.eldoria.companies.commands.suggest.SoloOrderIds;
import de.eldoria.companies.commands.suggest.SoloOrderLines;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.DeliveryResult;
import de.eldoria.companies.orders.SoloOrderService.Result;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * {@code /order work}: orders a player works on alone, without a company.
 * <ul>
 *     <li>without arguments it lists the orders the player works on alone, or opens the search when there are
 *     none;</li>
 *     <li>{@code search …} finds orders to work on alone, with the same filters as the company search;</li>
 *     <li>{@code info <id>}, {@code accept <id>} and {@code deliver <id> <line> <amount|max>} or
 *     {@code deliver <id> all} work like their company counterparts;</li>
 *     <li>{@code abort <id>} gives an order back after the player confirmed it. The delivered items are returned and
 *     the player cannot take a solo order until the cooldown passed.</li>
 * </ul>
 * The checks, the events and the changes are made by {@link de.eldoria.companies.orders.SoloOrderService}.
 */
@Subcommand("work")
@PlayerOnly
public final class WorkCommand {
    private final CommandDependencies deps;
    private final SoloOrderViews views;
    private final ConfirmFlow<FullOrder> aborts;

    @Subcommand
    public final CompanyOrderSearchCommand search;

    /**
     * @param deps what the commands need
     */
    public WorkCommand(CommandDependencies deps) {
        this.deps = deps;
        this.views = new SoloOrderViews(deps);
        this.aborts = ConfirmFlow.create(deps.plugin(), this::abortConfirmed);
        var finder = new SoloSearchExecutor(
                deps.orderData(),
                () -> deps.services().soloOrders(),
                () -> deps.services().deliveryService().matcher(),
                () -> deps.configuration() != null
                        && deps.configuration().systemOrderSettings().markSystemOrders(),
                orders -> deps.services().offerData().marks(orders));
        this.search = new CompanyOrderSearchCommand(new OrderSearch(
                deps, SearchMode.SOLO, finder, (player, id) -> info(player, id), (player, id) -> accept(player, id)));
        views.bind(this);
    }

    /**
     * Lists the orders the sender works on alone, or opens the search when there are none.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("list")
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        views.showOrders(player);
    }

    /**
     * Shows an order the sender works on alone, or an order nobody accepted yet.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("info")
    public void info(CommandSender sender, @SoloOrderIds int id) {
        var player = Senders.player(sender);
        var soloOrders = deps.services().soloOrders();
        var orderData = deps.orderData();
        orderData
                .retrieveOrderById(id)
                .asFuture()
                .thenAccept(found -> {
                    var order = found.filter(candidate -> visible(player, candidate.state(), candidate.soloWorker()));
                    if (order.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var fullOrder = orderData.toFullOrder(order.get());
                    var eligible = soloOrders.eligibility(player).join().isEmpty();
                    views.renderOrder(player, fullOrder, eligible);
                })
                .exceptionally(this::logged);
    }

    /**
     * Claims an order for the sender to work on alone.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("accept")
    public void accept(CommandSender sender, @SearchResultIds @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        deps.services()
                .soloOrders()
                .accept(player, id)
                .thenAccept(result -> {
                    switch (result) {
                        case Result.Refused<FullOrder> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case Result.Done<FullOrder> accepted -> {
                            CompanyOrderSources.invalidate(player.getUniqueId());
                            menus.close(player).thenRun(() -> views.renderOrder(player, accepted.value(), true));
                        }
                    }
                })
                .exceptionally(this::logged);
    }

    /**
     * Delivers an amount of items to one line.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param line   line of the order, from one
     * @param amount the most items to deliver
     */
    @Executes("deliver")
    public void deliver(
            CommandSender sender,
            @SoloOrderIds int id,
            @SoloOrderLines @IntArg(min = 1) int line,
            @IntArg(min = 1) int amount) {
        deliverLine(Senders.player(sender), id, line, amount);
    }

    /**
     * Delivers as many items to one line as it misses.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param line   line of the order, from one
     * @param max    the literal {@code max}
     */
    @Executes("deliver")
    public void deliverMax(
            CommandSender sender,
            @SoloOrderIds int id,
            @SoloOrderLines @IntArg(min = 1) int line,
            @Literal("max") String max) {
        deliverLine(Senders.player(sender), id, line, Integer.MAX_VALUE);
    }

    /**
     * Delivers every matching item to every line.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param all    the literal {@code all}
     */
    @Executes("deliver")
    public void deliverAll(CommandSender sender, @SoloOrderIds int id, @Literal("all") String all) {
        var player = Senders.player(sender);
        var service = deps.services().deliveryService();
        startDelivery(player, id, order -> Optional.of(service.deliverAllSolo(player, order)));
    }

    /**
     * Asks the sender to confirm giving an order back.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("abort")
    public void abort(CommandSender sender, @SoloOrderIds int id) {
        var player = Senders.player(sender);
        var services = deps.services();
        services.soloOrders()
                .workerOrder(player, id)
                .thenAccept(result -> {
                    switch (result) {
                        case Result.Refused<FullOrder> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case Result.Done<FullOrder> found ->
                            aborts.ask(services.menus(), player, found.value(), abortQuestion(found.value()));
                    }
                })
                .exceptionally(this::logged);
    }

    /**
     * Gives back the order the sender confirmed to abort.
     *
     * @param sender sender of the command
     */
    @Executes("abort confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        aborts.confirm(player);
    }

    private MenuText abortQuestion(FullOrder order) {
        var minutes = deps.services().soloOrders().settings().abortCooldownMinutes();
        var name = Values.text("order_name", order.fullName());
        if (minutes <= 0) return MenuText.create().locale("solo.abort.confirm", name);
        return MenuText.create()
                .locale(
                        "solo.abort.confirmCooldown",
                        name,
                        Values.text("duration", Texts.prettyDuration(Duration.ofMinutes(minutes))));
    }

    private void abortConfirmed(Player player, FullOrder order) {
        deps.services()
                .soloOrders()
                .abort(player, order.id())
                .thenAccept(result -> {
                    switch (result) {
                        case Result.Refused<?> refused -> refused.failure().sendTo(player, deps.messages());
                        case Result.Done<?> done -> {
                            CompanyOrderSources.invalidate(player.getUniqueId());
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            "solo.aborted",
                                            Replacement.unparsed("order_name", order.fullName()));
                            views.showOrders(player);
                        }
                    }
                })
                .exceptionally(this::logged);
    }

    private void deliverLine(Player player, int id, int line, int amount) {
        var service = deps.services().deliveryService();
        startDelivery(player, id, order -> {
            if (order.line(line - 1).isEmpty()) {
                deps.messages().sendErrorActionBar(player, "error.unknownLine");
                return Optional.empty();
            }
            return Optional.of(service.deliverSolo(player, order, line - 1, amount));
        });
    }

    /**
     * Loads the order the player works on alone and runs the delivery on the main thread.
     *
     * @param player   delivering player
     * @param id       id of the order
     * @param delivery takes the items; empty when it refused
     */
    private void startDelivery(
            Player player, int id, Function<FullOrder, Optional<CompletableFuture<DeliveryResult>>> delivery) {
        deps.services()
                .soloOrders()
                .workerOrder(player, id)
                .thenAccept(result -> {
                    switch (result) {
                        case Result.Refused<FullOrder> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case Result.Done<FullOrder> found ->
                            Threading.mainThread(deps.plugin())
                                    .execute(() -> deliverOnMainThread(player, found.value(), delivery));
                    }
                })
                .exceptionally(this::logged);
    }

    private void deliverOnMainThread(
            Player player, FullOrder order, Function<FullOrder, Optional<CompletableFuture<DeliveryResult>>> delivery) {
        if (!player.isOnline()) return;
        delivery.apply(order).ifPresent(running -> running.thenAccept(delivered -> {
                    CompanyOrderSources.invalidate(player.getUniqueId());
                    if (delivered.orderDone()) {
                        deps.services().menus().close(player);
                        return;
                    }
                    views.renderOrder(player, delivered.order(), true);
                })
                .exceptionally(this::logged));
    }

    private static boolean visible(Player player, OrderState state, Optional<UUID> soloWorker) {
        if (state == OrderState.UNCLAIMED) return true;
        return state == OrderState.CLAIMED
                && soloWorker.map(player.getUniqueId()::equals).orElse(false);
    }

    private Void logged(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }
}
