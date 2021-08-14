package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.utils.EnumUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Permission extends EldoCommand {
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;

    public Permission(Plugin plugin, ACompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);
        if (args.length == 1) {
            var memberName = args[0];
            renderPermissionInterface(player, memberName);
            return true;
        }

        if (argumentsInvalid(sender, args, 3, "<member> <add|remove> <permission>")) return true;

        var memberName = args[0];
        var method = args[1];
        if (!("give".equalsIgnoreCase(method) || "remove".equalsIgnoreCase(method))) {
            messageSender().sendError(sender, "Invalid action. Use ADD or REMOVE");
            return true;
        }
        var permission = EnumUtil.parse(args[2], CompanyPermission.class);
        if (permission == null || permission == CompanyPermission.OWNER) {
            messageSender().sendError(sender, "Invalid permission");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    var self = profile.member(player).get();
                    if (!self.hasPermissions(CompanyPermission.MANAGE_PERMISSIONS, permission)) {
                        messageSender().sendError(sender, "Missing permission");
                        return;
                    }
                    var optTarget = profile.memberByName(memberName);
                    if (optTarget.isEmpty()) {
                        messageSender().sendError(sender, "Unkown member");
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
        return true;
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
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
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
