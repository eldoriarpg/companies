package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Kick extends EldoCommand {
    private final CompanyData companyData;

    public Kick(Plugin plugin, CompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<name>")) return true;
        if (denyConsole(sender)) return true;

        var player = getPlayerFromSender(sender);

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a guild");
                        return;
                    }
                    var profile = optProfile.get();

                    if (!profile.member(player).map(r -> r.hasPermissions(CompanyPermission.KICK)).orElse(false)) {
                        messageSender().sendError(sender, "You are not allowed to kick users");
                        return;
                    }

                    var optMember = profile.memberByName(args[0]);

                    if (optMember.isEmpty()) {
                        messageSender().sendError(sender, "Not part of the company");
                        return;
                    }
                    var member = optMember.get();
                    companyData.submitMemberUpdate(member.kick());
                    messageSender().sendMessage(sender, "You kicked " + member.player().getName());
                    if (!member.player().isOnline()) return;
                    var target = member.player().getPlayer();
                    messageSender().sendMessage(target, "You were kicked from your company.");
                });
        return true;
    }
}
