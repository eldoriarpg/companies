/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Permission extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final MiniMessage miniMessage;
    private final BukkitAudiences audiences;
    private final MessageBlocker messageBlocker;

    public Permission(Plugin plugin, ACompanyData companyData, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("permission")
                .addArgument("member", true)
                .build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.miniMessage();
        this.messageBlocker = messageBlocker;
    }

    private void renderPermissionInterface(Player player, String memberName) {
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    var self = profile.member(player).get();
                    if (!self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.permission.managePermissions");
                        return;
                    }
                    var companyMember = profile.memberByName(memberName);
                    if (companyMember.isEmpty()) {
                        messageSender().sendLocalizedMessage(player, "error.invalidMember");
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
                builder.text("<click:run_command:%s remove %s><u><%s>[$%s$]</u></click>", permCmd, permission.name(), Colors.ADD, permission.translationKey()).build();
            } else {
                builder.text("<click:run_command:%s give %s><u><%s>[$%s$]</u></click>", permCmd, permission.name(), Colors.REMOVE, permission.translationKey()).build();
            }
            permissions.add(builder.build());
        }

        var composer = MessageComposer.create()
                .text("<%s>", Colors.HEADING).localeCode("company.permission.permissions").text("<%s> %s:", Colors.VALUE, member.player().getName()).newLine()
                .text(permissions, " ");
        if (messageBlocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>").build();
        }
        messageBlocker.announce(player, "[x]");
        composer.prependLines(25);
        audiences.player(player).sendMessage(miniMessage.deserialize(localizer().localize(composer.build())));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if (arguments.size() == 1) {
            var memberName = arguments.asString(0);
            renderPermissionInterface(player, memberName);
            return;
        }

        CommandAssertions.invalidArguments(meta(), arguments,
                Argument.input("words.member", true),
                Argument.unlocalizedInput("add|remove", true),
                Argument.input("words.permission", true));

        var memberName = arguments.asString(0);
        var method = arguments.asString(1);
        if (!("give".equalsIgnoreCase(method) || "remove".equalsIgnoreCase(method))) {
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.invalidAction",
                    Replacement.create("first", "GIVE").addFormatting('4'), Replacement.create("second", "REMOVE").addFormatting('c'));
            return;
        }
        var permission = arguments.asEnum(2, CompanyPermission.class);
        if (permission == CompanyPermission.OWNER) {
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.ownerPermission");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    var self = profile.member(player).get();
                    if (!self.hasPermissions(CompanyPermission.MANAGE_PERMISSIONS, permission)) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.permission.givePermissions");
                        return;
                    }
                    var optTarget = profile.memberByName(memberName);
                    if (optTarget.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.invalidMember");
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.isEmpty()) {
            return List.of("give", "remove");
        }
        if (args.sizeIs(1)) {
            return TabCompleteUtil.complete(args.asString(0), "give", "remove");
        }
        if (args.sizeIs(2)) {
            var stream = Arrays.stream(CompanyPermission.values())
                    .filter(p -> p != CompanyPermission.OWNER)
                    .map(p -> p.name().toLowerCase(Locale.ROOT));
            return TabCompleteUtil.complete(args.asString(1), stream);
        }
        return Collections.emptyList();
    }
}
