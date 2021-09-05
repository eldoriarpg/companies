package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final BukkitAudiences audiences;

    public Permission(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("permission")
                .addArgument("member", true)
                .build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    private void renderPermissionInterface(Player player, String memberName) {
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    var self = profile.member(player).get();
                    if (!self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                        messageSender().sendError(player, "Missing permission");
                        return;
                    }
                    var companyMember = profile.memberByName(memberName);
                    if (companyMember.isEmpty()) {
                        messageSender().sendMessage(player, "Unkown member");
                        return;
                    }
                    renderPermissionInterface(player, companyMember.get());
                });
    }

    private void renderPermissionInterface(Player player, CompanyMember member) {
        List<Component> permissions = new ArrayList<>();
        for (var permission : CompanyPermission.values()) {
            if (permission == CompanyPermission.OWNER) continue;
            var builder = Component.text();
            if (member.hasPermission(permission)) {
                var cmd = "/company permission " + member.player().getName() + " remove " + permission.name();
                builder.append(Component.text(permission.name(), NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("remove"))));
            } else {
                var cmd = "/company permission " + member.player().getName() + " give " + permission.name();
                builder.append(Component.text(permission.name(), NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("give"))));
            }
            permissions.add(builder.build());
        }

        var permComp = Component.join(Component.space(), permissions);
        var message = Component.text("Permissions of " + member.player().getName())
                .append(Component.newline())
                .append(permComp);
        audiences.player(player).sendMessage(message);
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if (arguments.size() == 1) {
            var memberName = arguments.asString(0);
            renderPermissionInterface(player, memberName);
            return;
        }

        CommandAssertions.invalidArguments(meta(), arguments,
                Argument.input("member", true),
                Argument.unlocalizedInput("add|remove", true),
                Argument.input("permission", true));

        var memberName = arguments.asString(0);
        var method = arguments.asString(1);
        if (!("give".equalsIgnoreCase(method) || "remove".equalsIgnoreCase(method))) {
            messageSender().sendError(player, "Invalid action. Use ADD or REMOVE");
            return;
        }
        var permission = arguments.asEnum(2, CompanyPermission.class);
        if (permission == CompanyPermission.OWNER) {
            messageSender().sendError(player, "Owner permission is not allowed");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    var self = profile.member(player).get();
                    if (!self.hasPermissions(CompanyPermission.MANAGE_PERMISSIONS, permission)) {
                        messageSender().sendError(player, "Missing permission");
                        return;
                    }
                    var optTarget = profile.memberByName(memberName);
                    if (optTarget.isEmpty()) {
                        messageSender().sendError(player, "Unkown member");
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
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 0) {
            return List.of("give", "remove");
        }
        if (args.length == 1) {
            return TabCompleteUtil.complete(args[0], "give", "remove");
        }
        if (args.length == 2) {
            var stream = Arrays.stream(CompanyPermission.values())
                    .filter(p -> p != CompanyPermission.OWNER)
                    .map(p -> p.name().toLowerCase(Locale.ROOT));
            return TabCompleteUtil.complete(args[1], stream);
        }
        return Collections.emptyList();
    }
}
