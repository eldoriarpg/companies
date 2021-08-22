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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

public class Edit extends EldoCommand {
    private final Configuration configuration;
    private final Info info;

    public Edit(Plugin plugin, Configuration configuration, Info info) {
        super(plugin);
        this.configuration = configuration;
        this.info = info;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 3, "<level> <field> <value>")) {
            return true;
        }

        var levelNr = Parser.parseInt(args[0]);

        if (levelNr.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        var optLevel = configuration.companySettings().level(levelNr.getAsInt());
        if (optLevel.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        var level = optLevel.get();
        var field = args[1].toLowerCase(Locale.ROOT);
        var values = Arrays.copyOfRange(args, 2, args.length);

        var optInt = Parser.parseInt(values[0]);
        var optDouble = OptionalDouble.empty();
        switch (field) {
            case "name":
                level.levelName(String.join(" ", values));
                break;
            case "order_count":
                if (optInt.isEmpty()) {
                    messageSender().sendError(sender, "Invalid number");
                    return true;
                }
                level.requirement().orderCount(optInt.getAsInt());
                break;
            case "member_count":
                if (optInt.isEmpty()) {
                    messageSender().sendError(sender, "Invalid number");
                    return true;
                }
                level.requirement().memberCount(optInt.getAsInt());
                break;
            case "earned_money":
                optDouble = Parser.parseDouble(values[0]);
                if (optDouble.isEmpty()) {
                    messageSender().sendError(sender, "Invalid number");
                    return true;
                }
                level.requirement().earnedMoney(optDouble.getAsDouble());
                break;
            case "delivered_items":
                if (optInt.isEmpty()) {
                    messageSender().sendError(sender, "Invalid number");
                    return true;
                }
                level.requirement().deliveredItems(optInt.getAsInt());
                break;
            case "max_members":
                if (optInt.isEmpty()) {
                    messageSender().sendError(sender, "Invalid number");
                    return true;
                }
                level.settings().maxMembers(optInt.getAsInt());
                break;
            case "max_orders":
                if (optInt.isEmpty()) {
                    messageSender().sendError(sender, "Invalid number");
                    return true;
                }
                level.settings().maxOrders(optInt.getAsInt());
                break;
            default:
                messageSender().sendError(sender, "Unkown field");
                return true;
        }
        configuration.save();
        info.show(sender, level);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
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
