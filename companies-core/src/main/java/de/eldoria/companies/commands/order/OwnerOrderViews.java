/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.menus.order.OwnerOrderActions;
import de.eldoria.companies.menus.order.OwnerOrderListMenu;
import de.eldoria.companies.menus.order.OwnerOrderMenu;
import de.eldoria.companies.util.Threading;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * The menus of {@code /order}: the orders of a player and the owner's view of one order. Their buttons run the same
 * commands a player can type.
 * <p>
 * The commands acting on an order show these menus in turn, so they are {@link #bind(Commands) bound} once all of
 * them exist.
 */
public final class OwnerOrderViews implements OwnerOrderActions {
    private final CommandDependencies deps;
    private final AtomicReference<Commands> commands = new AtomicReference<>();

    /**
     * @param deps what the commands need
     */
    public OwnerOrderViews(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Binds the commands the buttons run.
     *
     * @param bound the commands
     * @throws IllegalStateException when the commands are bound already
     */
    public void bind(Commands bound) {
        if (!commands.compareAndSet(null, bound)) {
            throw new IllegalStateException("The order commands are bound already.");
        }
    }

    /**
     * Shows one order of a player to its owner.
     *
     * @param player owner of the order
     * @param id     id of the order
     */
    public void showOrder(Player player, int id) {
        var orderData = deps.orderData();
        orderData
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(order -> {
                    if (order.isEmpty() || !order.get().owner().equals(player.getUniqueId())) {
                        deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var fullOrder = orderData.retrieveFullOrder(order.get()).join();
                    if (fullOrder == null) {
                        deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var services = deps.services();
                    var menu = new OwnerOrderMenu(this, deps.messages(), services.economy());
                    services.menus().show(player, menu, OwnerOrderMenu.Model.of(fullOrder, deps.configuration()));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the orders of a player and runs an action once they are shown.
     *
     * @param player       owner of the orders
     * @param whenComplete runs after the menu was shown
     */
    public void showOrders(Player player, Runnable whenComplete) {
        var services = deps.services();
        var orderData = deps.orderData();
        var usage = deps.drafts().limits().usage(player).asFuture();
        var waiting = orderData
                .retrieveItemReturnCount(player.getUniqueId(), ReturnKind.OWNER)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not count waiting order items", err);
                    return 0;
                });
        orderData
                .retrieveOrdersByPlayer(player, OrderState.UNCLAIMED, OrderState.DELIVERED)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Collections.emptyList();
                })
                .thenApply(orders -> orderData
                        .retrieveFullOrders(orders)
                        .asFuture()
                        .exceptionally(err -> {
                            deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                            return Collections.emptyList();
                        })
                        .join())
                .thenCombine(
                        usage,
                        (orders, limits) ->
                                waiting.thenApply(count -> OwnerOrderListMenu.Model.of(orders, limits, count)))
                .thenCompose(model -> model)
                .thenAccept(model -> {
                    var menu = new OwnerOrderListMenu(this, deps.messages(), services.economy());
                    services.menus().show(player, menu, model);
                    whenComplete.run();
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    @Override
    public void info(Player player, int id) {
        showOrder(player, id);
    }

    @Override
    public void list(Player player) {
        showOrders(player, () -> {});
    }

    @Override
    public void cancel(Player player, int id) {
        guarded(player, () -> commands().cancel().cancel(player, id));
    }

    @Override
    public void extend(Player player, int id) {
        guarded(player, () -> commands().extend().extend(player, id));
    }

    @Override
    public void raise(Player player, int id, @Nullable String entered) {
        var amount = OwnerOrderMenu.raiseAmount(entered);
        if (amount.isEmpty()) {
            CommandFailure.actionBar("order.info.error.invalidRaise").sendTo(player, deps.messages());
            showOrder(player, id);
            return;
        }
        guarded(player, () -> commands().raise().raise(player, id, amount.get()));
    }

    @Override
    public void receive(Player player, int id) {
        guarded(player, () -> commands().receive().receive(player, id));
    }

    @Override
    public void collect(Player player) {
        var plugin = deps.plugin();
        deps.services()
                .menus()
                .close(player)
                .thenRunAsync(
                        () -> deps.services().itemReturnService().handOut(player, ReturnKind.OWNER, false),
                        Threading.mainThread(plugin));
    }

    @Override
    public void create(Player player, @Nullable String name) {
        guarded(player, () -> commands().create().create(player, name == null ? "" : name.strip()));
    }

    private void guarded(Player player, Runnable action) {
        try {
            action.run();
        } catch (CommandFailure failure) {
            failure.sendTo(player, deps.messages());
        }
    }

    private Commands commands() {
        return Objects.requireNonNull(commands.get(), "The order commands are not bound.");
    }

    /**
     * The commands the buttons run.
     *
     * @param cancel  cancels an order
     * @param extend  extends an order
     * @param raise   raises the price of an order
     * @param receive receives a delivered order
     * @param create  starts a new order
     */
    public record Commands(Cancel cancel, Extend extend, Raise raise, Receive receive, OrderCreateCommand create) {}
}
