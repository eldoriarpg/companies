package de.eldoria.companies.commands;

import de.eldoria.companies.commands.companyadmin.Level;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.plugin.Plugin;

public class CompanyAdmin extends EldoCommand {
    public CompanyAdmin(Plugin plugin, Configuration configuration, ACompanyData companyData) {
        super(plugin);
        registerCommand("level", new Level(plugin, configuration));
    }
}
