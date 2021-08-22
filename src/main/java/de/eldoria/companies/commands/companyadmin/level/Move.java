package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class Move extends EldoCommand {
    private final Configuration configuration;
    private final List list;

    public Move(Plugin plugin, Configuration configuration, List list) {
        super(plugin);
        this.configuration = configuration;
        this.list = list;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 2, "<source> <target>")) {
            return true;
        }

        var sourcePos = Parser.parseInt(args[0]);

        if (sourcePos.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        var optLevel = configuration.companySettings().level(sourcePos.getAsInt());
        if (optLevel.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        var targetPos = Parser.parseInt(args[1]);

        if (targetPos.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        configuration.companySettings().moveLevel(sourcePos.getAsInt(), targetPos.getAsInt());
        configuration.save();
        list.sendList(sender);
        return true;
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList("<source>");
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }

        if (args.length == 2) {
            if (args[0].isEmpty()) {
                return Collections.singletonList("<target>");
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }
        return Collections.emptyList();
    }
}
