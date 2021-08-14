package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Create;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends EldoCommand {
    public Order(Plugin plugin, AOrderData orderData, Configuration configuration, Economy economy) {
        super(plugin);
        var list = new List(plugin, orderData, economy);
        setDefaultCommand(list);
        registerCommand("cancel", new Cancel(plugin, orderData, economy));
        registerCommand("create", new Create(plugin, orderData, economy, configuration));
        registerCommand("info", new Info(plugin, orderData, economy));
        registerCommand("list", list);
        registerCommand("receive", new Receive(plugin, orderData));
    }
}
