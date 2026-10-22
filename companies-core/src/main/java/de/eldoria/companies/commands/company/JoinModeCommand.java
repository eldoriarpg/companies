/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.menus.MenuText;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /company joinmode [open|applications|invite]}: shows or changes how players join the sender's company.
 * Changing it needs the permission to edit the profile.
 */
@Subcommand("joinmode")
@PlayerOnly
public final class JoinModeCommand {
    private final CommandDependencies deps;
    private final MembershipReplies replies;

    /**
     * @param deps what the commands need
     */
    public JoinModeCommand(CommandDependencies deps) {
        this.deps = deps;
        this.replies = new MembershipReplies(deps);
    }

    /**
     * Shows the join mode of the sender's company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void show(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(profile ->
                        profile.ifPresentOrElse(company -> sendMode(player, company.joinMode()), () -> deps.messages()
                                .sendErrorActionBar(player, "error.noMember")))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Opens the company to every player.
     *
     * @param sender sender of the command
     */
    @Executes("open")
    public void open(CommandSender sender) {
        change(sender, JoinMode.OPEN);
    }

    /**
     * Lets players apply to the company.
     *
     * @param sender sender of the command
     */
    @Executes("applications")
    public void applications(CommandSender sender) {
        change(sender, JoinMode.APPLICATIONS);
    }

    /**
     * Lets players join only when invited.
     *
     * @param sender sender of the command
     */
    @Executes("invite")
    public void invite(CommandSender sender) {
        change(sender, JoinMode.INVITE_ONLY);
    }

    private void change(CommandSender sender, JoinMode mode) {
        var player = Senders.player(sender);
        replies.reply(player, deps.services().membershipService().setJoinMode(player, mode));
    }

    private void sendMode(Player player, JoinMode mode) {
        var text = MenuText.create()
                .locale("company.joinMode.current")
                .text(" <value><i18n:" + mode.translationKey() + ">");
        deps.messages().sendMessage(player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }
}
