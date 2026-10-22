/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Abort extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Map<UUID, SimpleOrder> cancel = new ConcurrentHashMap<>();
    private final List list;
    private final ItemReturnService itemReturnService;


    public Abort(Plugin plugin, ACompanyData companyData, AOrderData orderData, List list, ItemReturnService itemReturnService) {
        super(plugin, CommandMeta.builder("abort")
                .addArgument("words.id", true)
                .build());
        this.companyData = companyData;
        this.orderData = orderData;
        this.list = list;
        this.itemReturnService = itemReturnService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            confirm(player);
            return;
        }

        var id = arguments.asInt(0);

        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept((optCompany) -> {
                       var order = checkOrder(optCompany, player, id);
                       if (order.isEmpty()) {
                           return;
                       }

                       var composer = MessageComposer.create()
                                                     .text("<neutral>")
                                                     .localeCode("company.order.abort.confirm")
                                                     .text("<click:run_command:/company order abort confirm><remove>[")
                                                     .localeCode("words.confirm")
                                                     .text("]</click>");
                       cancel.put(player.getUniqueId(), order.get());
                       messageSender().sendMessage(player, composer);
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   })
        ;
    }

    private void confirm(@NotNull Player player) {
        var remove = cancel.remove(player.getUniqueId());
        if (remove == null) {
            messageSender().sendErrorActionBar(player, "error.noConfirm");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(optCompany -> {
                       var id = remove.id();
                       if (checkOrder(optCompany, player, id).isEmpty()) {
                           return;
                       }

                       var workers = orderData.submitUnclaimOrder(remove)
                                              .join();
                       if (workers.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.orderNotActive");
                           return;
                       }
                       itemReturnService.notifyWorkers(workers.get());

                       list.showOrders(SimpleCompany.forId(remove.company()), player, () ->
                               plugin().getServer()
                                       .getPluginManager()
                                       .callEvent(new OrderCanceledEvent(remove, optCompany.get())));
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   });
    }

    private Optional<SimpleOrder> checkOrder(Optional<CompanyProfile> optCompany, Player player, int id) {
        if (optCompany.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }

        var company = optCompany.get();

        var optOrder = orderData.retrieveOrderById(id)
                                .join();
        if (optOrder.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.unkownOrder");
            return Optional.empty();
        }

        var order = optOrder.get();
        if (order.company() != company.id()) {
            messageSender().sendErrorActionBar(player, "error.orderNotOwned");
            return Optional.empty();
        }

        if (order.state() != OrderState.CLAIMED) {
            messageSender().sendErrorActionBar(player, "error.orderNotActive");
            return Optional.empty();
        }

        if (!company.member(player)
                    .get()
                    .hasPermissions(CompanyPermission.MANAGE_ORDERS)) {
            messageSender().sendErrorActionBar(player, "error.permission.cancelOrder");
            return Optional.empty();
        }
        return optOrder;
    }
}
