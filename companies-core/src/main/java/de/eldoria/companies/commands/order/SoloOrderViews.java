/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.company.CompanyOrderActions;
import de.eldoria.companies.menus.company.CompanyOrderListMenu;
import de.eldoria.companies.menus.company.CompanyOrderMenu;
import de.eldoria.companies.util.Times;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * The menus of {@code /order work}: the orders a player works on alone and the view of one order. They are the menus
 * of the company orders, with the solo tax and buttons that run the {@code /order work} commands.
 * <p>
 * The commands acting on an order show these menus in turn, so the command is {@link #bind(WorkCommand) bound} once
 * it exists.
 */
public final class SoloOrderViews implements CompanyOrderActions {
    private final CommandDependencies deps;
    private final AtomicReference<WorkCommand> command = new AtomicReference<>();

    /**
     * @param deps what the commands need
     */
    public SoloOrderViews(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Binds the command the buttons run.
     *
     * @param bound the command
     * @throws IllegalStateException when the command is bound already
     */
    public void bind(WorkCommand bound) {
        if (!command.compareAndSet(null, bound)) {
            throw new IllegalStateException("The work command is bound already.");
        }
    }

    /**
     * Shows an order to a player who works on it alone or could take it.
     *
     * @param player    viewing player
     * @param order     order to show
     * @param canAccept whether the player may take and give up orders alone
     */
    public void renderOrder(Player player, FullOrder order, boolean canAccept) {
        var services = deps.services();
        var model = CompanyOrderMenu.Model.of(
                order,
                order.deliveryHours(deps.configuration()),
                services.soloOrders().settings().taxPercent(),
                canAccept,
                services.deliveryService().matcher(),
                Times.now());
        deps.withOfferAndEventsOf(order, OptionalInt.empty(), model.taxPercent(), (offer, events) -> {
            if (!player.isOnline()) return;
            services.menus()
                    .show(
                            player,
                            new CompanyOrderMenu(this, deps.messages(), services.economy()),
                            model.withOffer(offer).withEvents(events));
        });
    }

    /**
     * Shows the orders a player works on alone, or the search for orders to work on when there are none.
     *
     * @param player viewing player
     */
    public void showOrders(Player player) {
        var services = deps.services();
        services.soloOrders()
                .workerOrders(player.getUniqueId())
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return List.of();
                })
                .thenAccept(orders -> {
                    if (orders.isEmpty()) {
                        search(player);
                        return;
                    }
                    var taxPercent = services.soloOrders().settings().taxPercent();
                    var menu = new CompanyOrderListMenu(this, deps.messages(), services.economy());
                    services.menus().show(player, menu, CompanyOrderListMenu.Model.of(orders, taxPercent));
                });
    }

    @Override
    public String deliverCommand() {
        return "/order work deliver";
    }

    @Override
    public String listTitle() {
        return "solo.list.title";
    }

    @Override
    public void info(Player player, int id) {
        guarded(player, () -> command().info(player, id));
    }

    @Override
    public void list(Player player) {
        guarded(player, () -> command().list(player));
    }

    @Override
    public void search(Player player) {
        guarded(player, () -> command().search.render(player));
    }

    @Override
    public void deliver(Player player, int id, int line, int amount) {
        guarded(player, () -> command().deliver(player, id, line, amount));
    }

    @Override
    public void deliverAll(Player player, int id) {
        guarded(player, () -> command().deliverAll(player, id, "all"));
    }

    @Override
    public void accept(Player player, int id) {
        guarded(player, () -> command().accept(player, id));
    }

    @Override
    public void abort(Player player, int id) {
        guarded(player, () -> command().abort(player, id));
    }

    private void guarded(Player player, Runnable action) {
        try {
            action.run();
        } catch (CommandFailure failure) {
            failure.sendTo(player, deps.messages());
        }
    }

    private WorkCommand command() {
        return Objects.requireNonNull(command.get(), "The work command is not bound.");
    }
}
