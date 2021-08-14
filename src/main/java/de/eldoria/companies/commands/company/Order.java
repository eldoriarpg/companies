package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.order.Abort;
import de.eldoria.companies.commands.company.order.Accept;
import de.eldoria.companies.commands.company.order.Deliver;
import de.eldoria.companies.commands.company.order.Info;
import de.eldoria.companies.commands.company.order.List;
import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends EldoCommand {
    public Order(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        var list = new List(plugin, companyData, orderData, economy);
        setDefaultCommand(list);
        registerCommand("abort", new Abort(plugin, companyData, orderData, list));
        registerCommand("accept", new Accept(plugin, companyData, orderData, configuration));
        registerCommand("deliver", new Deliver(plugin, companyData, orderData, economy));
        registerCommand("list", list);
        registerCommand("info", new Info(plugin, companyData,orderData, economy));
        registerCommand("search", new Search(plugin, orderData, economy));
    }
}
