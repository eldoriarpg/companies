package de.eldoria.companies.commands;

import de.eldoria.companies.commands.company.Create;
import de.eldoria.companies.commands.company.Invite;
import de.eldoria.companies.commands.company.Kick;
import de.eldoria.companies.commands.company.Leave;
import de.eldoria.companies.commands.company.Member;
import de.eldoria.companies.commands.company.Order;
import de.eldoria.companies.commands.company.Permission;
import de.eldoria.companies.commands.company.Profile;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Company extends EldoCommand {
    public Company(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        var profile = new Profile(plugin, companyData, orderData);
        setDefaultCommand(profile);
        registerCommand("create", new Create(plugin, companyData, economy, configuration));
        registerCommand("create", profile);
        registerCommand("invite", new Invite(plugin, companyData, configuration));
        registerCommand("kick", new Kick(plugin, companyData));
        registerCommand("leave", new Leave(plugin, companyData, orderData));
        registerCommand("member", new Member(plugin, companyData));
        registerCommand("order", new Order(plugin, companyData, orderData, economy, configuration));
        registerCommand("permission", new Permission(plugin, companyData));
    }
}
