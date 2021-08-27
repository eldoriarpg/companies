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
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Order extends AdvancedCommand {
    public Order(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin);
        var meta = CommandMeta.builder("order")
                .buildSubCommands((commands, builder) -> {
                    var list = new List(plugin, companyData, orderData, economy);
                    builder.withDefaultCommand(list);
                    commands.add(new Abort(plugin, companyData, orderData, list));
                    commands.add(new Accept(plugin, companyData, orderData, configuration));
                    commands.add(new Deliver(plugin, companyData, orderData, economy, configuration));
                    commands.add(list);
                    commands.add(new Info(plugin, companyData, orderData, economy, configuration));
                    commands.add(new Search(plugin, orderData, economy, messageBlocker));
                })
                .build();
        meta(meta);
    }}
