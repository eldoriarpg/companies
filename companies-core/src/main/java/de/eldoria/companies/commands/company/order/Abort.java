/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyOrderIds;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /company order abort <id>}: gives an order the company works on back after the member confirmed it.
 * <p>
 * The delivered items go back to their workers as item returns, and the order is open for other companies again.
 */
@Subcommand("abort")
@PlayerOnly
public final class Abort {
    private final CommandDependencies deps;
    private final CompanyOrderViews views;
    private final Confirmations<SimpleOrder> cancel = new Confirmations<>();

    /**
     * @param deps  what the commands need
     * @param views the menus of the company orders
     */
    public Abort(CommandDependencies deps, CompanyOrderViews views) {
        this.deps = deps;
        this.views = views;
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
        deps.requireReady();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optCompany -> {
                    var order = checkOrder(optCompany, player, id);
                    if (order.isEmpty()) {
                        return;
                    }

                    var composer = MessageComposer.create()
                            .text("<neutral>")
                            .localeCode("company.order.abort.confirm")
                            .space()
                            .text("<click:run_command:/company order abort confirm><remove>[")
                            .localeCode("words.confirm")
                            .text("]</click>");
                    cancel.request(player.getUniqueId(), order.get());
                    messages().sendMessage(player, composer);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Aborts the order the sender confirmed to abort.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        var itemReturnService = deps.services().itemReturnService();
        var optRemove = cancel.confirm(player.getUniqueId());
        if (optRemove.isEmpty()) {
            messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var remove = optRemove.get();

        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optCompany -> {
                    if (checkOrder(optCompany, player, remove.id()).isEmpty()) {
                        return;
                    }

                    var workers = deps.orderData().submitUnclaimOrder(remove).join();
                    if (workers == null || workers.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.orderNotActive");
                        return;
                    }
                    CompanyOrderSources.invalidate(player.getUniqueId());
                    itemReturnService.notifyWorkers(workers.get());

                    views.showOrders(SimpleCompany.forId(remove.company()), player, () -> deps.plugin()
                            .getServer()
                            .getPluginManager()
                            .callEvent(new OrderCanceledEvent(remove, optCompany.get())));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private Optional<SimpleOrder> checkOrder(Optional<CompanyProfile> optCompany, Player player, int id) {
        if (optCompany.isEmpty()) {
            messages().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }

        var company = optCompany.get();

        var optOrder = deps.orderData().retrieveOrderById(id).join();
        if (optOrder == null || optOrder.isEmpty()) {
            messages().sendErrorActionBar(player, "error.unknownOrder");
            return Optional.empty();
        }

        var order = optOrder.get();
        if (order.company() != company.id()) {
            messages().sendErrorActionBar(player, "error.orderNotOwned");
            return Optional.empty();
        }

        if (order.state() != OrderState.CLAIMED) {
            messages().sendErrorActionBar(player, "error.orderNotActive");
            return Optional.empty();
        }

        if (!company.member(player).get().hasPermissions(CompanyPermission.MANAGE_ORDERS)) {
            messages().sendErrorActionBar(player, "error.permission.cancelOrder");
            return Optional.empty();
        }
        return optOrder;
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
