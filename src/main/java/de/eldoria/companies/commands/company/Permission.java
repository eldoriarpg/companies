package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.EnumUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);
        if (args.length == 1) {
            var memberName = args[0];
            renderPermissionInterface(player, memberName);
            return true;
        }

        if (argumentsInvalid(sender, args, 3, "<member> <add|remove> <permission>")) return true;

        var memberName = args[0];
        var method = args[1];
        if (!("add".equalsIgnoreCase(method) || "remote".equalsIgnoreCase(method))) {
            messageSender().sendError(sender, "Invalid action. Use ADD or REMOVE");
            return true;
        }
        var permission = EnumUtil.parse(args[2], CompanyPermission.class);
        if (permission == null) {
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
                    if (!optTarget.isPresent()) {
                        messageSender().sendError(sender, "Unkown member");
                        return;
                    }

                    renderPermissionInterface(player, optTarget.get());
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
                    if (self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
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
                builder.append(Component.text(permission.name())
                        .clickEvent(ClickEvent.runCommand(cmd)));
            } else {
                var cmd = "/company permission " + member.player().getName() + " add " + permission.name();
                builder.append(Component.text(permission.name())
                        .clickEvent(ClickEvent.runCommand(cmd)));
            }
            permissions.add(builder.build());
        }

        var permComp = Component.join(Component.space(), permissions);
        var message = Component.text("Permissions of " + member.player().getName())
                .append(Component.newline())
                .append(permComp);
        audiences.player(player).sendMessage(message);
    }
}
