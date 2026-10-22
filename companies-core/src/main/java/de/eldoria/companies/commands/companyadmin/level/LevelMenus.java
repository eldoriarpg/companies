/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.util.Menus;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;

import java.util.ArrayList;

/**
 * The chat menus of the company level editor: the list of levels and the editor of one level.
 */
final class LevelMenus {
    private LevelMenus() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Shows every level with buttons to inspect, remove and move it.
     *
     * @param player         player who opened the menu
     * @param configuration  configuration holding the levels
     * @param messageBlocker blocker holding the chat while the menu is open
     */
    static void list(Player player, Configuration configuration, MessageBlocker messageBlocker) {
        var level = new ArrayList<String>();
        messageBlocker.blockPlayer(player);
        for (var companyLevel : configuration.companySettings().level()) {
            var info = MessageComposer.create()
                    .text(
                            "<hover:show_text:'%s'><name>%s - <value>%s</hover>",
                            companyLevel.asComponent(), companyLevel.level(), companyLevel.levelName())
                    .space()
                    .text("<click:run_command:/companyadmin level info %s><show>[", companyLevel.level())
                    .localeCode("words.info")
                    .text("]</click>")
                    .space()
                    .text("<click:run_command:/companyadmin level remove %s><remove>[", companyLevel.level())
                    .localeCode("words.remove")
                    .text("]</click>")
                    .space()
                    .text("<click:suggest_command:/companyadmin level move %s ><modify>[", companyLevel.level())
                    .localeCode("words.move")
                    .text("]</click>")
                    .build();
            level.add(info);
        }
        var builder = MessageComposer.create()
                .text("<heading>")
                .localeCode("words.level")
                .text(" <click:suggest_command:/companyadmin level create ><add>[")
                .localeCode("words.create")
                .text("]</click>")
                .newLine()
                .text(String.join("\n", level));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        builder.prependLines(25);
        Menus.show(messageBlocker, player, builder);
    }

    /**
     * Shows one level with a change button for each field.
     *
     * @param player         player who opened the menu
     * @param level          the level
     * @param messageBlocker blocker holding the chat while the menu is open
     */
    static void info(Player player, CompanyLevel level, MessageBlocker messageBlocker) {
        messageBlocker.blockPlayer(player);
        var cmd = "/companyadmin level";
        var edit = cmd + " edit " + level.level();
        var builder = MessageComposer.create()
                .text("<heading>")
                .localeCode("words.level")
                .text(" %s ", level.level())
                .text("<click:suggest_command:'%s move %s '><modify>[", cmd, level.level())
                .localeCode("words.move")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("words.name")
                .text(": <name>%s ", level.levelName())
                .text("<click:suggest_command:%s name ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<heading>")
                .localeCode("level.requirements")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.orderCount")
                .text(": <value>%s ", level.requirement().orderCount())
                .text("<click:suggest_command:%s order_count ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.memberCount")
                .text(": <value>%s ", level.requirement().memberCount())
                .text("<click:suggest_command:%s member_count ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.earnedMoney")
                .text(": <value>%s ", level.requirement().earnedMoney())
                .text("<click:suggest_command:%s earned_money ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.deliveredItems")
                .text(": <value>%s ", level.requirement().deliveredItems())
                .text("<click:suggest_command:%s delivered_items ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<heading>")
                .localeCode("level.limits")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.maxMember")
                .text(": <value>%s ", level.settings().maxMembers())
                .text("<click:suggest_command:%s max_members ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.maxOrders")
                .text(": <value>%s ", level.settings().maxOrders())
                .text("<click:suggest_command:%s max_orders ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.maxOrderSize")
                .text(": <value>%s ", level.settings().maxOrderSize())
                .text("<click:suggest_command:%s max_order_size ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.deliveryHoursBonus")
                .text(": <value>%s ", level.settings().deliveryHoursBonus())
                .text("<click:suggest_command:%s delivery_hours_bonus ><modify>[", edit)
                .localeCode("words.change")
                .text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        builder.prependLines(25);
        Menus.show(messageBlocker, player, builder);
    }
}
