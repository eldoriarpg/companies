package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.company.member.Id;
import de.eldoria.companies.commands.company.member.Self;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.plugin.Plugin;

public class Member extends EldoCommand {
    public Member(Plugin plugin, ACompanyData companyData) {
        super(plugin);
        var self = new Self(plugin, companyData);
        setDefaultCommand(self);
        registerCommand("self", self);
        registerCommand("id", new Id(plugin, companyData));
    }
}
