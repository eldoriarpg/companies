package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class Move extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final List list;

    public Move(Plugin plugin, Configuration configuration, List list) {
        super(plugin, CommandMeta.builder("move")
                .addArgument("words.source", true)
                .addArgument("words.target", true)
                .build());
        this.configuration = configuration;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String label, @NotNull Arguments args) throws CommandException {
        var optLevel = configuration.companySettings().level(args.asInt(0));
        CommandAssertions.isTrue(optLevel.isPresent(), "error.invalidLevel");
        configuration.companySettings().moveLevel(args.asInt(0), args.asInt(1));
        configuration.save();
        list.sendList(sender);
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList(localizer().localize("words.source"));
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }

        if (args.length == 2) {
            if (args[0].isEmpty()) {
                return Collections.singletonList(localizer().localize("words.target"));
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }
        return Collections.emptyList();
    }
}
