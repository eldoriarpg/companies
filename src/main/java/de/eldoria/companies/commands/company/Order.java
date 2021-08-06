package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.order.Abort;
import de.eldoria.companies.commands.company.order.Accept;
import de.eldoria.companies.commands.company.order.Deliver;
import de.eldoria.companies.commands.company.order.List;
import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends EldoCommand {
    public Order(Plugin plugin, CompanyData companyData, OrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        registerCommand("abort", new Abort(plugin, companyData, orderData));
        registerCommand("accept", new Accept(plugin, companyData, orderData, configuration));
        registerCommand("deliver", new Deliver(plugin, companyData, orderData, economy));
        registerCommand("list", new List(plugin, companyData, orderData, economy));
        registerCommand("search", new Search(plugin, orderData, economy));
    }
}
