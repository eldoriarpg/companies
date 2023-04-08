/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Self extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final MessageBlocker messageBlocker;

    public Self(Plugin plugin, ACompanyData companyData, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("self").build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.miniMessage();
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    messageBlocker.blockPlayer(player);
                    var builder = MessageComposer.create().text("<heading>").localeCode("company.member.members").text(":").newLine();

                    List<String> members = new ArrayList<>();
                    var self = optProfile.get().member(player).get();

                    for (var member : optProfile.get().members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hover = MessageComposer.create();

                        hover.text(((CompanyMember) member).statusComponent());

                        if (!member.permissions().isEmpty()) {
                            var permissions = member.permissions().stream()
                                    .map(perm -> "  " + perm.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.toList());
                            hover.newLine().text("<heading>").localeCode("words.permissions").text(":").newLine()
                                    .text("<active>").text(permissions, ", ");
                        }
                        var nameComp = MessageComposer.create().text("<hover:show_text:'%s'>%s</hover>", hover.build(), mem.getName());

                        if (self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                            nameComp = nameComp.space().text("<click:run_command:/company permission %s><modify>[", mem.getName()).localeCode("words.permissions").text("]</click>");
                        }
                        members.add(nameComp.build());
                    }
                    builder.text(members);
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    messageBlocker.announce(player, "[x]");
                    builder.prependLines(25);
                    audiences.player(player).sendMessage(miniMessage.deserialize(localizer().localize(builder.build())));
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
