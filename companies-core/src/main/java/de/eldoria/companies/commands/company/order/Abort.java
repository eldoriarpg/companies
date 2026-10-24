/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.CompanyOrderService.Aborted;
import de.eldoria.companies.services.ActionResult;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /company order abort <id>}: gives an order the company works on back after the member confirmed it.
 * <p>
 * The delivered items go back to their workers as item returns, and the order is open for other companies again. The
 * checks and the abort are made by {@link CompanyOrderService}.
 */
@Subcommand("abort")
@PlayerOnly
public final class Abort {
    private final CommandDependencies deps;
    private final CompanyOrderViews views;
    private final ConfirmFlow<Integer> aborts;

    /**
     * @param deps  what the commands need
     * @param views the menus of the company orders
     */
    public Abort(CommandDependencies deps, CompanyOrderViews views) {
        this.deps = deps;
        this.views = views;
        this.aborts = ConfirmFlow.create(deps.plugin(), this::abortConfirmed);
    }

    /**
     * Asks the sender to confirm aborting an order.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void abort(CommandSender sender, @CompanyOrderIds int id) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        deps.services()
                .companyOrders()
                .abortable(player.getUniqueId(), id)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<Aborted> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<Aborted> done ->
                            aborts.ask(
                                    menus,
                                    player,
                                    done.value().order().id(),
                                    MenuText.create().locale("company.order.abort.confirm"));
                    }
                })
                .exceptionally(this::log);
    }

    /**
     * Aborts the order the sender confirmed to abort.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        aborts.confirm(player);
    }

    private void abortConfirmed(Player player, int id) {
        deps.services()
                .companyOrders()
                .abort(player.getUniqueId(), id)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<Aborted> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<Aborted> done ->
                            views.showOrders(
                                    SimpleCompany.forId(done.value().order().company()), player, () -> {});
                    }
                })
                .exceptionally(this::log);
    }

    private Void log(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }
}
