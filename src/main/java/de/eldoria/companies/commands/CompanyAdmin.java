package de.eldoria.companies.commands;

import de.eldoria.companies.commands.companyadmin.Level;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import org.bukkit.plugin.Plugin;

public class CompanyAdmin extends AdvancedCommand {
    public CompanyAdmin(Plugin plugin, Configuration configuration, ACompanyData companyData, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("companyadmin")
                .withSubCommand(new Level(plugin, configuration, messageBlocker))
                .build());
    }
}
