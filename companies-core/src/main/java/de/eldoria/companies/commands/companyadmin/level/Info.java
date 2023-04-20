/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Info(Plugin plugin, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("info")
                .addArgument("words.level", true)
                .build());
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var levelNr = arguments.asInt(0);

        var optLevel = configuration.companySettings()
                                    .level(levelNr);
        if (optLevel.isEmpty()) {
            messageSender().sendErrorActionBar(player, "Invalid level");
            return;
        }

        show(player, optLevel.get());
    }

    public void show(Player player, CompanyLevel level) {
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
                                     .text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            builder.newLine()
                   .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);
        messageSender().sendMessage(player, builder);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.sizeIs(1)) {
            if (args.asString(0)
                    .isEmpty()) {
                return Collections.singletonList(localizer().localize("words.index"));
            }
            return Completion.completeInt(args.asString(0), 1, configuration.companySettings()
                                                                            .level()
                                                                            .size());
        }
        return Collections.emptyList();
    }
}
