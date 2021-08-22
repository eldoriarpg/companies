package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.companyadmin.level.Create;
import de.eldoria.companies.commands.companyadmin.level.Edit;
import de.eldoria.companies.commands.companyadmin.level.List;
import de.eldoria.companies.commands.companyadmin.level.Move;
import de.eldoria.companies.commands.companyadmin.level.Info;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.plugin.Plugin;

public class Level extends EldoCommand {
    public Level(Plugin plugin, Configuration configuration) {
        super(plugin);
        var list = new List(plugin, configuration);
        var info = new Info(plugin, configuration);
        setDefaultCommand(list);
        registerCommand("create", new Create(plugin, configuration, info));
        registerCommand("edit", new Edit(plugin, configuration, info));
        registerCommand("list", list);
        registerCommand("move", new Move(plugin, configuration, list));
        registerCommand("info", info);
    }
}
