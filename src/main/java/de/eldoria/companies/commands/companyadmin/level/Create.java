package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.ITabExecutor;
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
import java.util.Optional;
import java.util.OptionalInt;

public class Create extends AdvancedCommand implements ITabExecutor {
    private final Configuration configuration;
    private final Info info;

    public Create(Plugin plugin, Configuration configuration, Info info) {
        super(plugin, CommandMeta.builder("create").addArgument("index", false).build());
        this.configuration = configuration;
        this.info = info;
    }

    @Override
    public void onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var position = ArgumentUtils.getOptionalParameter(arguments.asArray(), 0, Optional.of(Integer.MAX_VALUE), Parser::parseInt);

        if (position.isEmpty()) {
            messageSender().sendError(sender, "Invalid number");
            return;
        }

        var level = configuration.companySettings().createLevel(position.get());
        configuration.save();
        info.show(sender, level);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList("<insert at>");
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size() + 1, localizer());
        }
        return Collections.emptyList();
    }
}
