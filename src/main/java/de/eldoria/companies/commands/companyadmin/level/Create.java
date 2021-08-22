package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.utils.ArgumentUtils;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

public class Create extends EldoCommand {
    private final Configuration configuration;
    private final Info info;

    public Create(Plugin plugin, Configuration configuration, Info info) {
        super(plugin);
        this.configuration = configuration;
        this.info = info;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var position = ArgumentUtils.getOptionalParameter(args, 0, OptionalInt.of(Integer.MAX_VALUE), Parser::parseInt);

        if (position.isEmpty()) {
            messageSender().sendError(sender, "Invalid number");
            return true;
        }

        var level = configuration.companySettings().createLevel(position.getAsInt());
        configuration.save();
        info.show(sender, level);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList("<insert at>");
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }
        return Collections.emptyList();
    }
}
