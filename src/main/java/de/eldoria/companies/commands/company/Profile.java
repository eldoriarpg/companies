package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.profile.Id;
import de.eldoria.companies.commands.company.profile.Name;
import de.eldoria.companies.commands.company.profile.Player;
import de.eldoria.companies.commands.company.profile.Self;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import org.bukkit.plugin.Plugin;

public class Profile extends AdvancedCommand {
    public Profile(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration) {
        super(plugin);
        var self = new Self(plugin, companyData, orderData, configuration);
        var meta = CommandMeta.builder("profile")
                .withDefaultCommand(self)
                .withSubCommand(self)
                .withSubCommand(new Id(plugin, companyData, configuration))
                .withSubCommand(new Name(plugin, companyData, configuration))
                .withSubCommand(new Player(plugin, companyData, configuration))
                .build();
        meta(meta);
    }
}
