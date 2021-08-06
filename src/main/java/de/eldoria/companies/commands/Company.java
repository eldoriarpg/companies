package de.eldoria.companies.commands;

import de.eldoria.companies.commands.company.Create;
import de.eldoria.companies.commands.company.Invite;
import de.eldoria.companies.commands.company.Kick;
import de.eldoria.companies.commands.company.Leave;
import de.eldoria.companies.commands.company.Member;
import de.eldoria.companies.commands.company.Order;
import de.eldoria.companies.commands.company.Permission;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Company extends EldoCommand {
    public Company(Plugin plugin, CompanyData companyData, OrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        registerCommand("create", new Create(plugin));
        registerCommand("invite", new Invite(plugin));
        registerCommand("kick", new Kick(plugin));
        registerCommand("leave", new Leave(plugin));
        registerCommand("member", new Member(plugin));
        registerCommand("order", new Order(plugin, companyData, orderData, economy, configuration));
        registerCommand("permission", new Permission(plugin));
    }
}
