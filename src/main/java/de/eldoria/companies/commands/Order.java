package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Create;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends EldoCommand {
    public Order(Plugin plugin, Configuration configuration, Economy economy, CompanyData companyData) {
        super(plugin);
        registerCommand("cancel", new Cancel(plugin, companyData, economy));
        registerCommand("create", new Create(plugin, configuration, economy, companyData));
        registerCommand("info", new Info(plugin, companyData, economy));
        registerCommand("list", new List(plugin, companyData, economy));
        registerCommand("receive", new Receive(plugin, companyData));
    }
}
