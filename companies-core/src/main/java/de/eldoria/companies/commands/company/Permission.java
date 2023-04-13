/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Permission extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final MessageBlocker messageBlocker;

    public Permission(Plugin plugin, ACompanyData companyData, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("permission")
                .addArgument("member", true)
                .build());
        this.companyData = companyData;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if (arguments.size() == 1) {
            var memberName = arguments.asString(0);
            renderPermissionInterface(player, memberName);
            return;
        }

        CommandAssertions.invalidArguments(meta(), arguments,
                Argument.input("words.member", true),
                Argument.unlocalizedInput("add|remove", true),
                Argument.input("words.permission", true));

        var memberName = arguments.asString(0);
        var method = arguments.asString(1);
        if (!("give".equalsIgnoreCase(method) || "remove".equalsIgnoreCase(method))) {
            messageSender().sendErrorActionBar(player, "error.invalidAction",
                    Replacement.create("first", "GIVE", Style.style(NamedTextColor.DARK_RED)),
                    Replacement.create("second", "REMOVE", Style.style(NamedTextColor.RED)));
            return;
        }
        var permission = arguments.asEnum(2, CompanyPermission.class);
        if (permission == CompanyPermission.OWNER) {
            messageSender().sendErrorActionBar(player, "error.ownerPermission");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                   .whenComplete(optProfile -> {
                       if (optProfile.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }
                       var profile = optProfile.get();
                       var self = profile.member(player)
                                         .get();
                       if (!self.hasPermissions(CompanyPermission.MANAGE_PERMISSIONS, permission)) {
                           messageSender().sendErrorActionBar(player, "error.permission.givePermissions");
                           return;
                       }
                       var optTarget = profile.memberByName(memberName);
                       if (optTarget.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.invalidMember");
                           return;
                       }

                       var target = optTarget.get();

                       if ("give".equalsIgnoreCase(method)) {
                           target.addPermission(permission);
                       } else {
                           target.removePermission(permission);
                       }
                       companyData.submitMemberUpdate(target);

                       renderPermissionInterface(player, target);
                   });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.isEmpty()) {
            return List.of("give", "remove");
        }
        if (args.sizeIs(1)) {
            return Completion.complete(args.asString(0), "give", "remove");
        }
        if (args.sizeIs(2)) {
            var stream = Arrays.stream(CompanyPermission.values())
                               .filter(p -> p != CompanyPermission.OWNER)
                               .map(p -> p.name()
                                          .toLowerCase(Locale.ROOT));
            return Completion.complete(args.asString(1), stream);
        }
        return Collections.emptyList();
    }

    private void renderPermissionInterface(Player player, String memberName) {
        companyData.retrievePlayerCompanyProfile(player)
                   .whenComplete(optProfile -> {
                       if (optProfile.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }
                       var profile = optProfile.get();
                       var self = profile.member(player)
                                         .get();
                       if (!self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                           messageSender().sendErrorActionBar(player, "error.permission.managePermissions");
                           return;
                       }
                       var companyMember = profile.memberByName(memberName);
                       if (companyMember.isEmpty()) {
                           messageSender().sendMessage(player, "error.invalidMember");
                           return;
                       }
                       renderPermissionInterface(player, companyMember.get());
                   });
    }

    private void renderPermissionInterface(Player player, CompanyMember member) {
        messageBlocker.blockPlayer(player);
        List<String> permissions = new ArrayList<>();
        for (var permission : CompanyPermission.values()) {
            if (permission == CompanyPermission.OWNER) continue;
            var permCmd = "/company permission " + member.player()
                                                         .getName();
            var builder = MessageComposer.create();
            if (member.hasPermission(permission)) {
                builder.text("<click:run_command:%s remove %s><u><add>[<l18n:%s>]</u></click>", permCmd, permission.name(), permission.translationKey())
                       .build();
            } else {
                builder.text("<click:run_command:%s give %s><u><remove>[<l18n:%s>]</u></click>", permCmd, permission.name(), permission.translationKey())
                       .build();
            }
            permissions.add(builder.build());
        }

        var composer = MessageComposer.create()
                                      .text("<heading>")
                                      .localeCode("company.permission.permissions")
                                      .text("<value> %s:", member.player()
                                                                 .getName())
                                      .newLine()
                                      .text(permissions, " ");
        if (messageBlocker.isBlocked(player)) {
            composer.newLine()
                    .text("<click:run_command:/company chatblock false><red>[x]</red></click>")
                    .build();
        }
        messageBlocker.announce(player, "[x]");
        composer.prependLines(25);
        messageSender().sendMessage(player, composer.build());
    }
}
