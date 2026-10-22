/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Collect;
import de.eldoria.companies.commands.order.Create;
import de.eldoria.companies.commands.order.Extend;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.Raise;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.util.CommandMetaBuilder;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends AdvancedCommand {
    public Order(
            Plugin plugin,
            AOrderData orderData,
            Configuration configuration,
            Economy economy,
            MessageBlocker messageBlocker,
            ItemReturnService itemReturnService) {
        super(
                plugin,
                new CommandMetaBuilder("order")
                        .buildSubCommands((cmds, builder) -> {
                            var orderLimits = new OrderLimits(orderData, configuration::userSettings);
                            var list = new List(plugin, orderData, orderLimits, economy, messageBlocker);
                            builder.withDefaultCommand(list);
                            cmds.add(list);
                            cmds.add(new Cancel(
                                    plugin, orderData, economy, new Collector(plugin, economy, configuration), list));
                            cmds.add(
                                    new Create(plugin, orderData, orderLimits, economy, configuration, messageBlocker));
                            cmds.add(new Info(plugin, orderData, economy, configuration, messageBlocker));
                            cmds.add(new Receive(plugin, orderData, messageBlocker));
                            cmds.add(new Collect(plugin, itemReturnService));
                            cmds.add(new Extend(plugin, orderData, configuration));
                            cmds.add(new Raise(plugin, orderData, economy, configuration));
                        })
                        .build());
    }
}
