/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.company.MemberMenu;
import de.eldoria.companies.menus.company.MemberView;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * {@code /company member [self|id <id>]}: the members of the sender's company with their permissions, or the members
 * of another company.
 */
@Subcommand("member")
@PlayerOnly
public final class MemberCommand {
    private final CommandDependencies deps;
    private final BiConsumer<Player, String> permissions;

    /**
     * @param deps        what the commands need
     * @param permissions opens the permission editor for a member, by name
     */
    public MemberCommand(CommandDependencies deps, BiConsumer<Player, String> permissions) {
        this.deps = deps;
        this.permissions = permissions;
    }

    /**
     * Lists the members of the sender's company, with buttons to edit their permissions for those allowed to.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("self")
    public void self(CommandSender sender) {
        var player = Senders.player(sender);
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    show(player, optProfile.get());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Lists the members of a company by its id.
     *
     * @param sender sender of the command
     * @param id     id of the company
     */
    @Executes("id")
    public void id(CommandSender sender, int id) {
        var player = Senders.player(sender);
        deps.companyData()
                .retrieveCompanyById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optSimple -> {
                    if (optSimple.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    deps.companyData()
                            .retrieveCompanyProfile(optSimple.get())
                            .asFuture()
                            .exceptionally(err -> {
                                deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                                return Optional.empty();
                            })
                            .join()
                            .ifPresent(profile -> show(player, profile));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the members of a company.
     *
     * @param player  viewing player
     * @param profile the company
     */
    public void show(Player player, CompanyProfile profile) {
        var menus = deps.services().menus();
        var menu = new MemberMenu(deps.messages(), menus, permissions);
        menus.show(player, menu, MemberMenu.Model.of(MemberView.of(profile, player)));
    }
}
