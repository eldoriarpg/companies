/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Edit extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final Info info;

    public Edit(Plugin plugin, Configuration configuration, Info info) {
        super(plugin, CommandMeta.builder("edit")
                .addArgument("words.level", true)
                .addArgument("words.field", true)
                .addArgument("words.value", true)
                .build()
        );
        this.configuration = configuration;
        this.info = info;
    }


    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args) throws CommandException {
        CommandAssertions.isTrue(configuration.nodeSettings().nodeType() == NodeType.PRIMARY, "error.secondarynode");
        var optLevel = configuration.companySettings()
                                    .level(args.asInt(0));
        CommandAssertions.isTrue(optLevel.isPresent(), "error.invalidLevel");

        var level = optLevel.get();

        switch (args.asString(1)
                    .toLowerCase(Locale.ROOT)) {
            case "name" -> level.levelName(args.join(2));
            case "order_count" -> level.requirement()
                                       .orderCount(args.asInt(2));
            case "member_count" -> level.requirement()
                                        .memberCount(args.asInt(2));
            case "earned_money" -> level.requirement()
                                        .earnedMoney(args.asDouble(2));
            case "delivered_items" -> level.requirement()
                                           .deliveredItems(args.asInt(2));
            case "max_members" -> level.settings()
                                       .maxMembers(args.asInt(2));
            case "max_orders" -> level.settings()
                                      .maxOrders(args.asInt(2));
            default -> {
                messageSender().sendErrorActionBar(player, "error.unkownField");
                return;
            }
        }
        configuration.save();
        info.show(player, level);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.sizeIs(1)) {
            if (args.asString(0)
                    .isEmpty()) {
                return Collections.singletonList(localizer().localize("words.source"));
            }
            return Completion.completeInt(args.asString(0), 1, configuration.companySettings()
                                                                            .level()
                                                                            .size());
        }

        var field = args.asString(1);
        if (args.sizeIs(2)) {
            return Completion.complete(field, "name", "order_count", "member_count", "earned_money", "delivered_items", "max_members", "max_orders");
        }

        var value = args.asString(2);
        if (args.sizeIs(3)) {
            if (Completion.isCommand(value, "name")) {
                return Completion.completeFreeInput(value, 32, localizer().localize("words.name"));
            }

            if (Completion.isCommand(value, "order_count", "member_count", "delivered_items", "max_members", "max_orders")) {
                return Completion.completeMinInt(value, 0);
            }

            if (Completion.isCommand(value, "earned_money")) {
                return Completion.completeMinDouble(value, 0.0);
            }
        }

        return Collections.emptyList();
    }
}
