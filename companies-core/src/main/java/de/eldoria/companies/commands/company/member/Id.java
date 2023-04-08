/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Id extends AdvancedCommand implements IPlayerTabExecutor {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final ACompanyData companyData;
    private final MessageBlocker messageBlocker;

    public Id(Plugin plugin, ACompanyData companyData, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("id")
                .build());
        this.companyData = companyData;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var companyId = args.asInt(0);

        companyData.retrieveCompanyById(companyId)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(optSimple -> {
                       if (optSimple.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.unknownCompany");
                           return;
                       }
                       messageBlocker.blockPlayer(player);
                       var optProfile = companyData.retrieveCompanyProfile(optSimple.get())
                                                   .asFuture()
                                                   .exceptionally(err -> {
                                                       plugin().getLogger()
                                                               .log(Level.SEVERE, "Something went wrong", err);
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
                       if (messageBlocker.isBlocked(player)) {
                           builder.newLine()
                                  .text("<click:run_command:/company chatblock false><red>[x]</click>");
                       }
                       messageBlocker.announce(player, "[x]");
                       builder.prependLines(25);
                       messageSender().sendMessage(player, builder.build());
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   })
        ;
    }
}
