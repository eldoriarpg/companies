package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.profile.Id;
import de.eldoria.companies.commands.company.profile.Name;
import de.eldoria.companies.commands.company.profile.Player;
import de.eldoria.companies.commands.company.profile.Self;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.plugin.Plugin;

public class Profile extends EldoCommand {
    public Profile(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration) {
        super(plugin);
        var self = new Self(plugin, companyData, orderData, configuration);
        setDefaultCommand(self);
        registerCommand("self", self);
        registerCommand("id", new Id(plugin, companyData, configuration));
        registerCommand("name", new Name(plugin, companyData, configuration));
        registerCommand("player", new Player(plugin, companyData, configuration));
    }
}
