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
import de.eldoria.companies.menus.company.PermissionEdit;
import de.eldoria.companies.menus.company.PermissionMenu;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MemberService.MemberRef;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /company permission <member> [give|remove <permission>]}: shows and changes the permissions of a member.
 * <p>
 * The checks and the changes are made by {@link MemberService}.
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
        BukkitFutureResult.of(deps.services().members().permissions(player.getUniqueId(), MemberRef.byName(member)))
                .whenComplete(result -> {
                    switch (result) {
                        case ActionResult.Refused<PermissionEdit> refused -> {
                            if (MemberService.INVALID_MEMBER.equals(
                                    refused.failure().localeKey())) {
                                messages().sendMessage(player, MemberService.INVALID_MEMBER);
                                return;
                            }
                            refused.failure().sendTo(player, messages());
                        }
                        case ActionResult.Done<PermissionEdit> done ->
                            deps.services().menus().show(player, menu, done.value());
                    }
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
        apply(player, MemberRef.byName(member), wanted, edit -> deps.services()
                .menus()
                .show(player, menu, edit));
    }

    private PermissionMenu menu() {
        return new PermissionMenu(messages(), deps.services().menus(), this::change);
    }

    private void change(
            Player player, UUID target, Map<CompanyPermission, Boolean> wanted, Consumer<PermissionEdit> onApplied) {
        apply(player, MemberRef.byId(target), wanted, onApplied);
    }

    private void apply(
            Player player,
            MemberRef target,
            Map<CompanyPermission, Boolean> wanted,
            Consumer<PermissionEdit> onApplied) {
        BukkitFutureResult.of(deps.services().members().setPermissions(player.getUniqueId(), target, wanted))
                .whenComplete(result -> {
                    switch (result) {
                        case ActionResult.Refused<PermissionEdit> refused ->
                            refused.failure().sendTo(player, messages());
                        case ActionResult.Done<PermissionEdit> done -> onApplied.accept(done.value());
                    }
                });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
