package de.eldoria.companies.commands.company;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.scheduling.DelayedActions;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class Invite extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Map<UUID, InviteData> invites = new HashMap<>();
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final DelayedActions delayedActions;
    private final Configuration configuration;

    public Invite(Plugin plugin, ACompanyData companyData, Configuration configuration) {
        super(plugin, CommandMeta.builder("invite")
                .addArgument("name", true)
                .build());
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        delayedActions = DelayedActions.start(plugin);
        this.companyData = companyData;
        this.configuration = configuration;
    }

    private void deny(@NotNull Player player) {
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messageSender().sendError(player, "No pending invite");
            return;
        }
        var inviter = plugin().getServer().getPlayer(data.inviter);
        if (inviter != null) {
            messageSender().sendMessage(inviter, "Your invite was declined.");
        }
    }

    private void accept(@NotNull Player player) {
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messageSender().sendError(player, "No pending invite");
            return;
        }

        companyData.retrievePlayerCompany(player)
                .whenComplete(company -> {
                    if (company.isPresent()) {
                        messageSender().sendError(player, "You are already part of a company");
                        return;
                    }
                    var inviter = Bukkit.getOfflinePlayer(data.inviter);
                    companyData.retrieveCompanyProfile(data.company)
                            .whenComplete(profile -> {
                                handleInviteAccept(player, data, inviter, profile);
                            });

                });
    }

    private void handleInviteAccept(Player player, InviteData data, OfflinePlayer inviter, Optional<CompanyProfile> profile) {
        if (profile.isEmpty()) {
            messageSender().sendError(player, "The company does no longer exist.");
            return;
        }

        if (profile.get().members().size() >= configuration.companySettings().level(profile.get().level()).orElse(CompanyLevel.DEFAULT).settings().maxMembers()) {
            messageSender().sendError(player, "Company is already full");
            return;
        }

        companyData.submitMemberUpdate(CompanyMember.forCompany(data.company, player));
        messageSender().sendMessage(player, "You have joined the company");
        if (inviter.isOnline()) {
            messageSender().sendMessage(inviter.getPlayer(), player.getName() + " has accepted your invite.");
        }

        plugin().getServer().getPluginManager().callEvent(new CompanyJoinEvent(profile.get(), player));
    }

    private void scheduleInvite(Player inviter, Player target, SimpleCompany company) {
        messageSender().sendMessage(inviter, "Invited " + target.getName());
        var composer = MessageComposer.create().text("<%s>", Colors.NEUTRAL).localeCode("You have been invited to join the %NAME% company",
                        Replacement.create("NAME", String.format("<%s>%s<%s>", Colors.HEADING, company.name(), Colors.NEUTRAL)))
                .text("<click:run_command:/company invite accept>[").localeCode("accept").text("]</click>")
                .text("<click:run_command:/company invite deny>[").localeCode("deny").text("]</click>");
        audiences.sender(target).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
        invites.put(target.getUniqueId(), new InviteData(company, inviter.getUniqueId()));
        delayedActions.schedule(() -> expiredInvite(target.getUniqueId()), 600);
    }

    private void expiredInvite(UUID uuid) {
        var data = invites.get(uuid);
        if (data == null) return;
        var target = plugin().getServer().getPlayer(uuid);
        if (target != null) {
            messageSender().sendMessage(target, "Invite expired.");
        }
        var inviter = plugin().getServer().getPlayer(data.inviter);
        if (inviter != null) {
            messageSender().sendMessage(inviter, "Invite expired.");
        }
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("accept".equalsIgnoreCase(arguments.asString(0))) {
            accept(player);
            return;
        }

        if ("deny".equalsIgnoreCase(arguments.asString(0))) {
            deny(player);
            return;
        }

        var target = arguments.asPlayer(0);

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((company, err) -> {
                    if (err != null) {
                        plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                        return;
                    }
                    if (company.isEmpty()) {
                        messageSender().sendError(player, "error.noMember");
                        return;
                    }

                    if (!company.get().member(player).get().hasPermissions(CompanyPermission.INVITE)) {
                        messageSender().sendError(player, "You dont have the permission to invite users.");
                        return;
                    }
                    var profile = company.get();
                    if (profile.members().size() >= configuration.companySettings().level(profile.level()).orElse(CompanyLevel.DEFAULT).settings().maxMembers()) {
                        messageSender().sendError(player, "Your company has reached the member limit.");
                        return;
                    }

                    var targetCompany = companyData.retrievePlayerCompany(target).asFuture().join();
                    if (targetCompany.isPresent()) {
                        messageSender().sendError(player, "Player is already part of a company");
                        return;
                    }
                    scheduleInvite(player, target, company.get());
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return TabCompleteUtil.completePlayers(arguments.asString(0));
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
