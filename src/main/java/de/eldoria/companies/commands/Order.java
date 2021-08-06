package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Create;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends EldoCommand {
    public Order(Plugin plugin, CompanyData companyData, OrderData orderData, Configuration configuration, Economy economy) {
        super(plugin);
        registerCommand("cancel", new Cancel(plugin, companyData, orderData, economy));
        registerCommand("create", new Create(plugin, orderData, economy, configuration));
        registerCommand("info", new Info(plugin, orderData, economy));
        registerCommand("list", new List(plugin, orderData, economy));
        registerCommand("receive", new Receive(plugin, orderData));
    }
}
