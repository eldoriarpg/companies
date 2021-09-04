package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.ITabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Edit extends AdvancedCommand implements ITabExecutor {
    private final Configuration configuration;
    private final Info info;

    public Edit(Plugin plugin, Configuration configuration, Info info) {
        super(plugin, CommandMeta.builder("edit")
                .addArgument("level", true)
                .addArgument("field", true)
                .addArgument("value", true)
                .build()
        );
        this.configuration = configuration;
        this.info = info;
    }


    @Override
    public void onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull Arguments args) throws CommandException {
        var optLevel = configuration.companySettings().level(args.asInt(0));
        if (optLevel.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return;
        }

        var level = optLevel.get();

        switch (args.asString(1).toLowerCase(Locale.ROOT)) {
            case "name":
                level.levelName(args.join(2));
                break;
            case "order_count":
                level.requirement().orderCount(args.asInt(2));
                break;
            case "member_count":
                level.requirement().memberCount(args.asInt(2));
                break;
            case "earned_money":
                level.requirement().earnedMoney(args.asDouble(2));
                break;
            case "delivered_items":
                level.requirement().deliveredItems(args.asInt(2));
                break;
            case "max_members":
                level.settings().maxMembers(args.asInt(2));
                break;
            case "max_orders":
                level.settings().maxOrders(args.asInt(2));
                break;
            default:
                messageSender().sendError(sender, "Unkown field");
                return;
        }
        configuration.save();
        info.show(sender, level);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList("<source>");
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }

        var field = args[1];
        if (args.length == 2) {
            return TabCompleteUtil.complete(field, "name", "order_count", "member_count", "earned_money", "delivered_items", "max_members", "max_orders");
        }

        var value = args[2];
        if (args.length == 3) {
            if (TabCompleteUtil.isCommand(value, "name")) {
                return TabCompleteUtil.completeFreeInput(value, 32, "Company Name", localizer());
            }

            if (TabCompleteUtil.isCommand(value, "order_count", "member_count", "delivered_items", "max_members", "max_orders")) {
                return TabCompleteUtil.completeInt(value, 0, 100000000, localizer());
            }

            if (TabCompleteUtil.isCommand(value, "earned_money")) {
                return TabCompleteUtil.completeDouble(value, 0.0, 100000000000000.0, localizer());
            }
        }

        return Collections.emptyList();
    }
}
