/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyMembers;
import de.eldoria.companies.commands.suggest.CompanyPermissions;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.util.Menus;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /company permission <member> [give|remove <permission>]}: shows and changes the permissions of a member.
 */
@Subcommand("permission")
@PlayerOnly
public final class Permission {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Permission(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the permissions of a member, each a button that gives or removes it.
     *
     * @param sender sender of the command
     * @param member name of the member
     */
    @Executes
    public void show(CommandSender sender, @CompanyMembers String member) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messages().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            var self = profile.member(player).get();
            if (!self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                messages().sendErrorActionBar(player, "error.permission.managePermissions");
                return;
            }
            var companyMember = profile.memberByName(member);
            if (companyMember.isEmpty()) {
                messages().sendMessage(player, "error.invalidMember");
                return;
            }
            renderPermissionInterface(player, companyMember.get(), messageBlocker);
        });
    }

    /**
     * Gives a permission to a member or removes it.
     *
     * @param sender     sender of the command
     * @param member     name of the member
     * @param action     {@code give} or {@code remove}
     * @param permission the permission, by name
     */
    @Executes
    public void change(
            CommandSender sender,
            @CompanyMembers String member,
            @Literal({"give", "remove"}) String action,
            @CompanyPermissions String permission) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        var companyPermission = Checks.enumValue(permission, CompanyPermission.class);
        if (companyPermission == CompanyPermission.OWNER) {
            throw CommandFailure.actionBar("error.ownerPermission");
        }

        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messages().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            var self = profile.member(player).get();
            if (!self.hasPermissions(CompanyPermission.MANAGE_PERMISSIONS, companyPermission)) {
                messages().sendErrorActionBar(player, "error.permission.givePermissions");
                return;
            }
            var optTarget = profile.memberByName(member);
            if (optTarget.isEmpty()) {
                messages().sendErrorActionBar(player, "error.invalidMember");
                return;
            }

            var target = optTarget.get();

            if ("give".equals(action)) {
                target.addPermission(companyPermission);
            } else {
                target.removePermission(companyPermission);
            }
            deps.companyData().submitMemberUpdate(target);

            renderPermissionInterface(player, target, messageBlocker);
        });
    }

    private void renderPermissionInterface(Player player, CompanyMember member, MessageBlocker messageBlocker) {
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

    private MessageSender messages() {
        return deps.messages();
    }
}
