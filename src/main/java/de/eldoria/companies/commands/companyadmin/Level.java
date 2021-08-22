package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Level extends EldoCommand {
    private final Configuration configuration;

    public Level(Plugin plugin, Configuration configuration) {
        super(plugin);
        this.configuration = configuration;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            renderLevels();
            return true;
        }
        return super.onCommand(sender, command, label, args);
    }

    private void renderLevels() {
        List<String> level = new ArrayList<>();

        for (var companyLevel : configuration.companySettings().level()) {
            String info = MessageComposer.create().text("%s - %s", companyLevel.level(), companyLevel.levelName())
                    .text("<click:run_command:/companyadmin level show %s>[", companyLevel.level()).localeCode("info").text("]</click>").space()
                    .text("<click:suggest_command:/companyadmin level move %s >[", companyLevel.level()).localeCode("info").text("]</click>")
                    .build();
        }
    }
}
