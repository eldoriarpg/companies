package de.eldoria.companies.commands.company;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.scheduling.DelayedActions;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Invite extends EldoCommand {
    private final ACompanyData companyData;
    private final Map<UUID, InviteData> invites = new HashMap<>();
    private final BukkitAudiences audiences;
    private final DelayedActions delayedActions;
    private final Configuration configuration;

    public Invite(Plugin plugin, ACompanyData companyData, Configuration configuration) {
        super(plugin);
        audiences = BukkitAudiences.create(plugin);
        delayedActions = DelayedActions.start(plugin);
        this.companyData = companyData;
        this.configuration = configuration;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<name>")) return true;
        var player = getPlayerFromSender(sender);

        if ("accept".equalsIgnoreCase(args[0])) {
            accept(sender, player);
            return true;
        }

        if ("deny".equalsIgnoreCase(args[0])) {
            deny(sender, player);
            return true;
        }

        var target = getPlugin().getServer().getPlayer(args[0]);

        if (target == null) {
            messageSender().sendError(sender, "Invalid player");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company.");
                        return;
                    }

                    if (!company.get().member(player).get().hasPermissions(CompanyPermission.INVITE)) {
                        messageSender().sendError(sender, "You dont have the permission to invite users.");
                        return;
                    }
                    companyData.retrievePlayerCompany(target)
                            .whenComplete(targetCompany -> {
                                companyData.retrieveCompanyProfile(company.get())
                                        .whenComplete(optProfile -> {
                                            var profile = optProfile.get();
                                            if (profile.members().size() >= configuration.companySettings().maxMember()) {
                                                messageSender().sendError(sender, "Your company has reached the member limit.");
                                                return;
                                            }
                                        });
                                if (targetCompany.isPresent()) {
                                    messageSender().sendError(sender, "Player is already part of a company");
                                    return;
                                }
                                scheduleInvite(player, target, company.get());
                            });

                });
        return true;
    }

    private void deny(@NotNull CommandSender sender, Player player) {
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messageSender().sendError(sender, "No pending invite");
            return;
        }
        messageSender().sendMessage(player, "Invite expired.");
        var inviter = getPlugin().getServer().getPlayer(data.inviter);
        if (inviter != null) {
            messageSender().sendMessage(inviter, "Your invite was declined.");
        }
    }

    private void accept(@NotNull CommandSender sender, Player player) {
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messageSender().sendError(sender, "No pending invite");
            return;
        }

        companyData.retrievePlayerCompany(player)
                .whenComplete(company -> {
                    if (company.isPresent()) {
                        messageSender().sendError(sender, "You are already part of a company");
                        return;
                    }
                    var inviter = Bukkit.getOfflinePlayer(data.inviter);
                    companyData.retrieveCompanyProfile(data.company)
                            .whenComplete(profile -> {
                                handleInviteAccept(sender, player, data, inviter, profile);
                            });

                });
    }

    private void handleInviteAccept(@NotNull CommandSender sender, Player player, InviteData data, OfflinePlayer inviter, Optional<CompanyProfile> profile) {
        if (profile.isEmpty()) {
            messageSender().sendError(player, "The company does no longer exist.");
            return;
        }

        if (profile.get().members().size() >= configuration.companySettings().maxMember()) {
            messageSender().sendError(sender, "Company is already full");
            return;
        }

        companyData.submitMemberUpdate(CompanyMember.forCompany(data.company, player));
        messageSender().sendMessage(sender, "You have joined the company");
        if (inviter.isOnline()) {
            messageSender().sendMessage(inviter.getPlayer(), player.getName() + " has accepted your invite.");
        }
        for (var member : profile.get().members()) {
            if (!member.player().isOnline()) continue;
            messageSender().sendMessage(member.player().getPlayer(), player.getName() + " has joined the company.");
        }
    }

    private void scheduleInvite(Player inviter, Player target, SimpleCompany company) {
        messageSender().sendMessage(inviter, "Invited " + target.getName());
        audiences.player(target).sendMessage(Component.text().append(Component.text("You have been invited to join the " + company.name() + " company"))
                .append(Component.text("[Accept]").clickEvent(ClickEvent.runCommand("/company invite accept")))
                .append(Component.text("[Deny]").clickEvent(ClickEvent.runCommand("/company invite deny"))));
        invites.put(target.getUniqueId(), new InviteData(company, inviter.getUniqueId()));
        delayedActions.schedule(() -> expiredInvite(target.getUniqueId()), 600);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompleteUtil.completePlayers(args[0]);
    }

    private void expiredInvite(UUID uuid) {
        var data = invites.get(uuid);
        if (data == null) return;
        var target = getPlugin().getServer().getPlayer(uuid);
        if (target != null) {
            messageSender().sendMessage(target, "Invite expired.");
        }
        var inviter = getPlugin().getServer().getPlayer(data.inviter);
        if (inviter != null) {
            messageSender().sendMessage(inviter, "Invite expired.");
        }
    }

    private static class InviteData {
        private final SimpleCompany company;
        private final UUID inviter;

        private InviteData(SimpleCompany company, UUID inviter) {
            this.company = company;
            this.inviter = inviter;
        }
    }
}
