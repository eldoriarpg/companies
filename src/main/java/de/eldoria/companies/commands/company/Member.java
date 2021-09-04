package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.member.Id;
import de.eldoria.companies.commands.company.member.Self;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import org.bukkit.plugin.Plugin;

public class Member extends AdvancedCommand {
    public Member(Plugin plugin, ACompanyData companyData) {
        super(plugin);
        var self = new Self(plugin, companyData);
        var meta = CommandMeta.builder("member")
                .withDefaultCommand(self)
                .withSubCommand(self)
                .withSubCommand(new Id(plugin, companyData))
                .build();
        meta(meta);
    }
}
