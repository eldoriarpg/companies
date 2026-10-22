/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.util.Menus;
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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Permission extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final MessageBlocker messageBlocker;
    private final Cache<UUID, List<String>> memberNames =
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public Permission(Plugin plugin, ACompanyData companyData, MessageBlocker messageBlocker) {
        super(
                plugin,
                CommandMeta.builder("permission").addArgument("member", true).build());
        this.companyData = companyData;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        if (arguments.size() == 1) {
            var memberName = arguments.asString(0);
            renderPermissionInterface(player, memberName);
            return;
        }

        CommandAssertions.invalidArguments(
                meta(),
                arguments,
                Argument.input("words.member", true),
                Argument.unlocalizedInput("give|remove", true),
                Argument.input("words.permission", true));

        var memberName = arguments.asString(0);
        var method = arguments.asString(1);
        if (!("give".equalsIgnoreCase(method) || "remove".equalsIgnoreCase(method))) {
            messageSender()
                    .sendErrorActionBar(
                            player,
                            "error.invalidAction",
                            Replacement.create("first", "GIVE"),
                            Replacement.create("second", "REMOVE"));
            return;
        }
        var permission = arguments.asEnum(2, CompanyPermission.class);
        if (permission == CompanyPermission.OWNER) {
            messageSender().sendErrorActionBar(player, "error.ownerPermission");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messageSender().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            var self = profile.member(player).get();
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

    /**
     * Completes the member names of the player's company, then the action and then the permission.
     * <p>
     * Member names come from a short-lived cache, because completion must not wait for the database. On a miss the
     * names are loaded in the background and offered on the next completion.
     */
    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.sizeIs(1)) {
            return Completion.complete(args.asString(0), cachedMemberNames(player));
        }
        if (args.sizeIs(2)) {
            return Completion.complete(args.asString(1), "give", "remove");
        }
        if (args.sizeIs(3)) {
            var stream = Arrays.stream(CompanyPermission.values())
                    .filter(p -> p != CompanyPermission.OWNER)
                    .map(p -> p.name().toLowerCase(Locale.ROOT));
            return Completion.complete(args.asString(2), stream);
        }
        return Collections.emptyList();
    }

    private List<String> cachedMemberNames(Player player) {
        var names = memberNames.getIfPresent(player.getUniqueId());
        if (names != null) return names;
        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(optProfile -> memberNames.put(
                        player.getUniqueId(),
                        optProfile.map(Permission::memberNames).orElse(List.of())));
        return List.of();
    }

    private static List<String> memberNames(CompanyProfile profile) {
        return profile.members().stream()
                .map(member -> member.player().getName())
                .filter(Objects::nonNull)
                .toList();
    }

    private void renderPermissionInterface(Player player, String memberName) {
        companyData.retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messageSender().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            var self = profile.member(player).get();
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
            var permCmd = "/company permission " + member.player().getName();
            var builder = MessageComposer.create();
            if (member.hasPermission(permission)) {
                builder.text(
                                "<click:run_command:%s remove %s><u><add>[<l18n:%s>]</u></click>",
                                permCmd, permission.name(), permission.translationKey())
                        .build();
            } else {
                builder.text(
                                "<click:run_command:%s give %s><u><remove>[<l18n:%s>]</u></click>",
                                permCmd, permission.name(), permission.translationKey())
                        .build();
            }
            permissions.add(builder.build());
        }

        var composer = MessageComposer.create()
                .text("<heading>")
                .localeCode("company.permission.permissions")
                .text("<value> %s:", member.player().getName())
                .newLine()
                .text(permissions, " ");
        if (messageBlocker.isBlocked(player)) {
            composer.newLine()
                    .text("<click:run_command:/company chatblock false><red>[x]</red></click>")
                    .build();
        }
        composer.prependLines(25);
        Menus.show(messageBlocker, player, composer);
    }
}
