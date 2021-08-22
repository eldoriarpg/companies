package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.ArgumentUtils;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

public class Create extends EldoCommand {
    private final Configuration configuration;
    private final Show show;

    public Create(Plugin plugin, Configuration configuration, Show show) {
        super(plugin);
        this.configuration = configuration;
        this.show = show;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var position = ArgumentUtils.getOptionalParameter(args, 0, OptionalInt.of(Integer.MAX_VALUE), Parser::parseInt);

        if (position.isEmpty()) {
            messageSender().sendError(sender, "Invalid number");
            return true;
        }

        var level = configuration.companySettings().createLevel(position.getAsInt());
        show.show(sender, level);
        return true;
    }
}
