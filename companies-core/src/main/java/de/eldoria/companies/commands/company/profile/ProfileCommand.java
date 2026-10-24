/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.member.MemberCommand;
import de.eldoria.companies.commands.suggest.CompanyNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.menus.company.ProfileMenu;
import de.eldoria.companies.menus.company.ProfileView;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /company profile [self|id <id>|name <name>|player <player>]}: the profile of the sender's company, with
 * level progress, or the public profile of another company. {@code edit} changes the profile.
 */
@Subcommand("profile")
@PlayerOnly
public final class ProfileCommand {
    private final CommandDependencies deps;
    private final MemberCommand members;
    private final Consumer<Player> orders;

    @Subcommand
    public final ProfileEditCommand edit;

    /**
     * @param deps    what the commands need
     * @param members shows the member list of a company
     * @param orders  shows the orders the viewer's company works on
     */
    public ProfileCommand(CommandDependencies deps, MemberCommand members, Consumer<Player> orders) {
        this.deps = deps;
        this.members = members;
        this.orders = orders;
        this.edit = new ProfileEditCommand(deps);
    }

    /**
     * Shows the profile of the sender's company.
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
                .thenAccept((optProfile) -> {
                    if (optProfile.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }

                    var orders = deps.orderData()
                            .retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .join();
                    var stats = deps.companyData()
                            .retrieveCompanyStats(optProfile.get())
                            .asFuture()
                            .join();
                    showOwn(player, optProfile.get(), orders == null ? 0 : orders.size(), stats);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the public profile of a company by its id.
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
                .thenAccept(optComp -> {
                    if (optComp.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    var optProfile = deps.companyData()
                            .retrieveCompanyProfile(optComp.get())
                            .asFuture()
                            .exceptionally(err -> {
                                deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                                return Optional.empty();
                            })
                            .join();
                    if (optProfile.isEmpty()) return;
                    showExternal(player, optProfile.get());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the public profile of a company by its name.
     *
     * @param sender sender of the command
     * @param name   name of the company
     */
    @Executes("name")
    public void name(CommandSender sender, @CompanyNames @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        deps.companyData()
                .retrieveCompanyByName(name)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optComp -> {
                    if (optComp.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    var optProfile = deps.companyData()
                            .retrieveCompanyProfile(optComp.get())
                            .asFuture()
                            .join();
                    if (optProfile.isEmpty()) return;
                    showExternal(player, optProfile.get());
                });
    }

    /**
     * Shows the public profile of the company of an online player.
     *
     * @param sender sender of the command
     * @param target player whose company is shown
     */
    @Executes("player")
    public void player(CommandSender sender, Player target) {
        var player = Senders.player(sender);
        deps.companyData()
                .retrievePlayerCompanyProfile(target)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optCompany -> {
                    if (optCompany.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noCompany");
                        return;
                    }
                    showExternal(player, optCompany.get());
                });
    }

    private void showExternal(Player player, CompanyProfile profile) {
        var view = ProfileView.external(profile, deps.configuration().companySettings());
        deps.services().menus().show(player, menu(), view);
    }

    private void showOwn(Player player, CompanyProfile profile, int orderCount, @Nullable CompanyStats stats) {
        var view = ProfileView.own(
                profile,
                deps.configuration().companySettings(),
                stats,
                orderCount,
                ProfileService.mayEdit(profile, player));
        deps.services().menus().show(player, menu(), view);
    }

    private ProfileMenu menu() {
        return new ProfileMenu(deps.messages(), deps.services().economy(), new ProfileMenu.Navigation() {
            @Override
            public void members(Player viewer, ProfileView profile) {
                members.id(viewer, profile.id());
            }

            @Override
            public void orders(Player viewer) {
                orders.accept(viewer);
            }

            @Override
            public void edit(Player viewer) {
                edit.show(viewer);
            }
        });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
