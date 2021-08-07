package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class Kick extends EldoCommand {
    private final ACompanyData companyData;

    public Kick(Plugin plugin, ACompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<name>")) return true;
        var player = getPlayerFromSender(sender);

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    handleProfile(sender, args[0], player, optProfile);
                });
        return true;
    }

    private void handleProfile(@NotNull CommandSender sender, @NotNull String arg, Player player, Optional<CompanyProfile> optProfile) {
        if (optProfile.isEmpty()) {
            messageSender().sendError(sender, "You are not part of a guild");
            return;
        }
        var profile = optProfile.get();

        if (!profile.member(player).map(r -> r.hasPermissions(CompanyPermission.KICK)).orElse(false)) {
            messageSender().sendError(sender, "You are not allowed to kick users");
            return;
        }

        var optMember = profile.memberByName(arg);

        if (optMember.isEmpty()) {
            messageSender().sendError(sender, "Not part of the company");
            return;
        }

        var member = optMember.get();

        if (member.hasPermission(CompanyPermission.KICK)) {
            messageSender().sendError(sender, "You can not kick this user.");
            return;
        }

        companyData.submitMemberUpdate(member.kick());
        messageSender().sendMessage(sender, "You kicked " + member.player().getName());
        if (member.player().isOnline()) {
            var target = member.player().getPlayer();
            messageSender().sendMessage(target, "You were kicked from your company.");
        }

        for (var currMember : profile.members()) {
            if (currMember.uuid().equals(member.uuid())) continue;
            if (!currMember.player().isOnline()) continue;
            messageSender().sendMessage(currMember.player().getPlayer(), member.player().getName() + " was kicked from the company");
        }
    }
}
