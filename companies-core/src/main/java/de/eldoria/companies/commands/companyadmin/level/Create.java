package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final Info info;

    public Create(Plugin plugin, Configuration configuration, Info info) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("words.index", false)
                .build());
        this.configuration = configuration;
        this.info = info;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var position = arguments.asInt(0, Integer.MAX_VALUE);

        var level = configuration.companySettings().createLevel(position);
        configuration.save();
        info.show(player, level);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList(localizer().localize("words.index"));
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size() + 1, localizer());
        }
        return Collections.emptyList();
    }
}
