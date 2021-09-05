package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Kick extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;

    public Kick(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("kick")
                .addArgument("name", true)
                .build());
        this.companyData = companyData;
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

        plugin().getServer().getPluginManager().callEvent(new CompanyKickEvent(optProfile.get(), member.player()));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    handleProfile(player, arguments.asString(0), player, optProfile);
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
