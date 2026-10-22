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
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.company.PermissionEdit;
import de.eldoria.companies.menus.company.PermissionMenu;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
     * Shows the permissions of a member in the permission editor.
     *
     * @param sender sender of the command
     * @param member name of the member
     */
    @Executes
    public void show(CommandSender sender, @CompanyMembers String member) {
        var player = Senders.player(sender);
        var menu = menu();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            var editor = editor(player, optProfile, "error.permission.managePermissions");
            if (editor.isEmpty()) return;
            var target = optProfile.get().memberByName(member);
            if (target.isEmpty()) {
                messages().sendMessage(player, "error.invalidMember");
                return;
            }
            deps.services().menus().show(player, menu, new PermissionEdit(editor.get(), target.get()));
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
        var menu = menu();
        var companyPermission = Checks.enumValue(permission, CompanyPermission.class);
        if (companyPermission == CompanyPermission.OWNER) {
            throw CommandFailure.actionBar("error.ownerPermission");
        }
        var wanted = Map.of(companyPermission, "give".equals(action));

        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            var editor = editor(player, optProfile, "error.permission.givePermissions");
            if (editor.isEmpty()) return;
            var target = optProfile.get().memberByName(member);
            if (target.isEmpty()) {
                messages().sendErrorActionBar(player, "error.invalidMember");
                return;
            }
            apply(player, editor.get(), target.get(), wanted, edit -> deps.services()
                    .menus()
                    .show(player, menu, edit));
        });
    }

    private PermissionMenu menu() {
        return new PermissionMenu(messages(), deps.services().menus(), this::change);
    }

    private void change(
            Player player, UUID target, Map<CompanyPermission, Boolean> wanted, Consumer<PermissionEdit> onApplied) {
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            var editor = editor(player, optProfile, "error.permission.givePermissions");
            if (editor.isEmpty()) return;
            var member = optProfile.get().member(Bukkit.getOfflinePlayer(target));
            if (member.isEmpty()) {
                messages().sendErrorActionBar(player, "error.invalidMember");
                return;
            }
            apply(player, editor.get(), member.get(), wanted, onApplied);
        });
    }

    private void apply(
            Player player,
            CompanyMember editor,
            CompanyMember target,
            Map<CompanyPermission, Boolean> wanted,
            Consumer<PermissionEdit> onApplied) {
        var edit = new PermissionEdit(editor, target);
        if (!wanted.keySet().stream().allMatch(edit::editable)) {
            messages().sendErrorActionBar(player, "error.permission.givePermissions");
            return;
        }
        if (!edit.apply(wanted).isEmpty()) {
            deps.companyData().submitMemberUpdate(target);
        }
        onApplied.accept(edit);
    }

    /**
     * The member editing permissions, after checking that the player is in a company and may manage permissions.
     *
     * @param player       editing player
     * @param profile      company of the player
     * @param deniedLocale error shown when the player may not manage permissions
     * @return the editing member, or empty after the error was shown
     */
    private Optional<CompanyMember> editor(Player player, Optional<CompanyProfile> profile, String deniedLocale) {
        if (profile.isEmpty()) {
            messages().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }
        var editor = profile.get().member(player);
        if (editor.isEmpty() || !editor.get().hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
            messages().sendErrorActionBar(player, deniedLocale);
            return Optional.empty();
        }
        return editor;
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
