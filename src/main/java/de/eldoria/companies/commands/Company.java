package de.eldoria.companies.commands;

import de.eldoria.companies.commands.company.Chatblock;
import de.eldoria.companies.commands.company.Create;
import de.eldoria.companies.commands.company.Invite;
import de.eldoria.companies.commands.company.Kick;
import de.eldoria.companies.commands.company.Leave;
import de.eldoria.companies.commands.company.Member;
import de.eldoria.companies.commands.company.Order;
import de.eldoria.companies.commands.company.Permission;
import de.eldoria.companies.commands.company.Profile;
import de.eldoria.companies.commands.company.Rename;
import de.eldoria.companies.commands.company.Top;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Company extends AdvancedCommand {
    public Company(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin);
        var profile = new Profile(plugin, companyData, orderData, configuration, messageBlocker);
        var meta = CommandMeta.builder("company")
                .withDefaultCommand(profile)
                .withSubCommand(profile)
                .withSubCommand(new Create(plugin, companyData, economy, configuration))
                .withSubCommand(new Invite(plugin, companyData, configuration))
                .withSubCommand(new Kick(plugin, companyData))
                .withSubCommand(new Leave(plugin, companyData, orderData))
                .withSubCommand(new Member(plugin, companyData, messageBlocker))
                .withSubCommand(new Order(plugin, companyData, orderData, economy, configuration, messageBlocker))
                .withSubCommand(new Permission(plugin, companyData, messageBlocker))
                .withSubCommand(new Top(plugin, companyData, messageBlocker))
                .withSubCommand(new Rename(plugin, configuration, economy, companyData))
                .withSubCommand(new Chatblock(plugin, messageBlocker))
                .build();
        meta(meta);
    }
}
