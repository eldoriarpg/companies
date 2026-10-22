/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.LevelFields;
import de.eldoria.companies.commands.suggest.LevelNumbers;
import de.eldoria.companies.commands.suggest.LevelValues;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/**
 * {@code /companyadmin level edit <level> <field> <value>}: changes one field of a level.
 */
@Subcommand("edit")
public final class Edit {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Edit(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param level  number of the level
     * @param field  field to change
     * @param value  new value
     */
    @Executes
    public void edit(
            CommandSender sender,
            @LevelNumbers int level,
            @LevelFields String field,
            @LevelValues @StringArg(StringArgType.GREEDY) String value) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        var configuration = deps.configuration();
        Levels.requirePrimary(configuration);
        var companyLevel = Levels.level(configuration, level);

        switch (field.toLowerCase(Locale.ROOT)) {
            case "name" -> companyLevel.levelName(value);
            case "order_count" -> companyLevel.requirement().orderCount(Checks.integer(value));
            case "member_count" -> companyLevel.requirement().memberCount(Checks.integer(value));
            case "earned_money" -> companyLevel.requirement().earnedMoney(Checks.finiteDouble(value));
            case "delivered_items" -> companyLevel.requirement().deliveredItems(Checks.integer(value));
            case "max_members" -> companyLevel.settings().maxMembers(Checks.integer(value));
            case "max_orders" -> companyLevel.settings().maxOrders(Checks.integer(value));
            case "tax_percent" -> companyLevel.settings().taxPercent(taxPercent(value));
            case "max_order_size" -> companyLevel.settings().maxOrderSize(Math.max(0, Checks.integer(value)));
            case "delivery_hours_bonus" ->
                companyLevel.settings().deliveryHoursBonus(Math.max(0, Checks.integer(value)));
            default -> throw CommandFailure.actionBar("error.unkownField");
        }
        configuration.save();
        LevelMenus.info(player, companyLevel, menus);
    }

    /**
     * Parses a tax override: {@code none} clears it, a number is clamped to 0 to 100.
     *
     * @param value entered value
     * @return percentage, or null to use the global tax
     */
    static Double taxPercent(String value) {
        if ("none".equalsIgnoreCase(value.trim())) return null;
        return Math.clamp(Checks.finiteDouble(value), 0.0, 100.0);
    }
}
