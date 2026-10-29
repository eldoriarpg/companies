/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.CompanyOrderSearchCommand;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.company.CompanyOrderActions;
import de.eldoria.companies.menus.company.CompanyOrderListMenu;
import de.eldoria.companies.menus.company.CompanyOrderMenu;
import de.eldoria.companies.util.Times;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * The menus of {@code /company order}: the orders of a company and the company view of one order. Their buttons run
 * the same commands a member can type.
 * <p>
 * The commands acting on an order show these menus in turn, so they are {@link #bind(Commands) bound} once all of
 * them exist.
 */
public final class CompanyOrderViews implements CompanyOrderActions {
    private final CommandDependencies deps;
    private final AtomicReference<Commands> commands = new AtomicReference<>();

    /**
     * @param deps what the commands need
     */
    public CompanyOrderViews(CommandDependencies deps) {
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
            throw new IllegalStateException("The company order commands are bound already.");
        }
    }

    /**
     * Shows the company view of an order to a member.
     *
     * @param player  viewing player
     * @param company company of the player, whose level decides the tax shown
     * @param member  the player as member of the company
     * @param order   order to show
     */
    public void renderOrder(Player player, ISimpleCompany company, CompanyMember member, FullOrder order) {
        var services = deps.services();
        var configuration = deps.configuration();
        var model = CompanyOrderMenu.Model.of(
                order,
                order.deliveryHours(configuration),
                configuration.companySettings().taxPercent(company.level()),
                member.hasPermission(CompanyPermission.MANAGE_ORDERS),
                services.deliveryService().matcher(),
                Times.now());
        deps.withOfferOf(order.id(), offer -> {
            if (!player.isOnline()) return;
            services.menus()
                    .show(
                            player,
                            new CompanyOrderMenu(this, deps.messages(), services.economy()),
                            model.withOffer(offer));
        });
    }

    /**
     * Shows the orders the company of a player works on.
     *
     * @param player viewing player
     */
    public void showOrders(Player player) {
        deps.companyData()
                .retrievePlayerCompany(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    showOrders(company.get(), player, () -> {});
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the orders a company works on and runs an action once they are shown.
     *
     * @param company company of the player
     * @param player  viewing player
     * @param after   runs after the orders were shown
     */
    public void showOrders(ISimpleCompany company, Player player, Runnable after) {
        var services = deps.services();
        var orderData = deps.orderData();
        orderData
                .retrieveOrdersByCompany(company, OrderState.CLAIMED, OrderState.CLAIMED)
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
                .thenAccept(fullOrders -> {
                    var taxPercent = deps.configuration().companySettings().taxPercent(company.level());
                    var menu = new CompanyOrderListMenu(this, deps.messages(), services.economy());
                    services.menus().show(player, menu, CompanyOrderListMenu.Model.of(fullOrders, taxPercent));
                    after.run();
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    @Override
    public void info(Player player, int id) {
        guarded(player, () -> commands().info().info(player, id));
    }

    @Override
    public void list(Player player) {
        showOrders(player);
    }

    @Override
    public void search(Player player) {
        guarded(player, () -> commands().search().render(player));
    }

    @Override
    public void deliver(Player player, int id, int line, int amount) {
        guarded(player, () -> commands().deliver().deliver(player, id, line, amount));
    }

    @Override
    public void deliverAll(Player player, int id) {
        guarded(player, () -> commands().deliver().deliverAll(player, id, "all"));
    }

    @Override
    public void accept(Player player, int id) {
        guarded(player, () -> commands().accept().accept(player, id));
    }

    @Override
    public void abort(Player player, int id) {
        guarded(player, () -> commands().abort().abort(player, id));
    }

    private void guarded(Player player, Runnable action) {
        try {
            action.run();
        } catch (CommandFailure failure) {
            failure.sendTo(player, deps.messages());
        }
    }

    private Commands commands() {
        return Objects.requireNonNull(commands.get(), "The company order commands are not bound.");
    }

    /**
     * The commands the buttons run.
     *
     * @param info    shows an order
     * @param accept  accepts an order
     * @param abort   gives an order back
     * @param deliver delivers to an order
     * @param search  opens the order search
     */
    public record Commands(Info info, Accept accept, Abort abort, Deliver deliver, CompanyOrderSearchCommand search) {}
}
