package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.member.Id;
import de.eldoria.companies.commands.company.member.Self;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import org.bukkit.plugin.Plugin;

public class Member extends AdvancedCommand {
    public Member(Plugin plugin, ACompanyData companyData, IMessageBlockerService messageBlocker) {
        super(plugin);
        var self = new Self(plugin, companyData, messageBlocker);
        var meta = CommandMeta.builder("member")
                .withDefaultCommand(self)
                .withSubCommand(self)
                .withSubCommand(new Id(plugin, companyData, messageBlocker))
                .build();
        meta(meta);
    }
}
