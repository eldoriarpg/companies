package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Create;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.util.CommandMetaBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends AdvancedCommand {
    public Order(Plugin plugin, AOrderData orderData, Configuration configuration, Economy economy, IMessageBlockerService messageBlocker) {
        super(plugin,
                new CommandMetaBuilder("order")
                        .buildSubCommands((cmds, builder) -> {
                            var list = new List(plugin, orderData, economy, configuration, messageBlocker);
                            builder.withDefaultCommand(list);
                            cmds.add(list);
                            cmds.add(new Cancel(plugin, orderData, economy, list));
                            cmds.add(new Create(plugin, orderData, economy, configuration, messageBlocker));
                            cmds.add(new Info(plugin, orderData, economy, messageBlocker));
                            cmds.add(new Receive(plugin, orderData, messageBlocker));
                        })
                        .build());
    }
}
