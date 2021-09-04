package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.companyadmin.level.Create;
import de.eldoria.companies.commands.companyadmin.level.Edit;
import de.eldoria.companies.commands.companyadmin.level.Info;
import de.eldoria.companies.commands.companyadmin.level.List;
import de.eldoria.companies.commands.companyadmin.level.Move;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import org.bukkit.plugin.Plugin;

public class Level extends AdvancedCommand {
    public Level(Plugin plugin, Configuration configuration) {
        super(plugin, CommandMeta.builder("level")
                .buildSubCommands((commands, builder) -> {
                    var list = new List(plugin, configuration);
                    var info = new Info(plugin, configuration);
                    builder.withDefaultCommand(list);
                    commands.add(new Create(plugin, configuration, info));
                    commands.add(new Edit(plugin, configuration, info));
                    commands.add(list);
                    commands.add(new Move(plugin, configuration, list));
                    commands.add(info);
                })
                .build());
    }
}
