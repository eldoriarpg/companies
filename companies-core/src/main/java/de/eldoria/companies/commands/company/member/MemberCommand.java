/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /company member [self|id <id>]}: the members of the sender's company with their permissions, or the members
 * of another company.
 */
@Subcommand("member")
@PlayerOnly
public final class MemberCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public MemberCommand(CommandDependencies deps) {
        this.deps = deps;
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
        var menus = deps.services().menus();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var builder = MessageComposer.create()
                            .text("<heading>")
                            .localeCode("company.member.members")
                            .text(":")
                            .newLine();

                    List<String> members = new ArrayList<>();
                    var self = optProfile.get().member(player).get();

                    for (var member : optProfile.get().members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hover = MessageComposer.create();

                        hover.text(((CompanyMember) member).statusComponent());

                        if (!member.permissions().isEmpty()) {
                            var permissions = member.permissions().stream()
                                    .map(perm -> perm.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.toList());
                            hover.newLine()
                                    .text("<heading>")
                                    .localeCode("words.permissions")
                                    .text(":")
                                    .newLine()
                                    .text("<active>")
                                    .text(permissions, ", ");
                        }
                        var nameComp = MessageComposer.create()
                                .text("<hover:show_text:'%s'><value>%s</hover>", hover.build(), mem.getName());

                        if (self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                            nameComp = nameComp.space()
                                    .text("<click:run_command:/company permission %s><modify>[", mem.getName())
                                    .localeCode("words.permissions")
                                    .text("]</click>");
                        }
                        members.add(nameComp.build());
                    }
                    builder.text(members);
                    menus.show(player, builder);
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
        var menus = deps.services().menus();
        deps.companyData()
                .retrieveCompanyById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optSimple -> {
                    if (optSimple.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    var optProfile = deps.companyData()
                            .retrieveCompanyProfile(optSimple.get())
                            .asFuture()
                            .exceptionally(err -> {
                                deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                                return Optional.empty();
                            })
                            .join();
                    if (optProfile.isEmpty()) return;
                    var profile = optProfile.get();
                    var builder = MessageComposer.create()
                            .text("<heading>")
                            .localeCode("company.member.members")
                            .text(":")
                            .newLine();
                    List<String> members = new ArrayList<>();

                    for (var member : profile.members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hover = MessageComposer.create();
                        hover.text(((CompanyMember) member).statusComponent());

                        var nameComp = MessageComposer.create()
                                .space(2)
                                .text("<hover:show_text:%s><value>%s</hover>", hover.build(), mem.getName());
                        members.add(nameComp.build());
                    }
                    builder.text(members);
                    menus.show(player, builder);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
