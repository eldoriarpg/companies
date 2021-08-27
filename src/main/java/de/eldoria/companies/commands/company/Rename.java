package de.eldoria.companies.commands.company;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.scheduling.DelayedActions;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Rename extends EldoCommand {
    private final Map<UUID, String> confirm = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final Economy economy;
    private final ACompanyData companyData;
    private final DelayedActions delayedActions;

    public Rename(Plugin plugin, Configuration configuration, Economy economy, ACompanyData companyData) {
        super(plugin);
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
        this.economy = economy;
        this.companyData = companyData;
        delayedActions = DelayedActions.start(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<name>")) {
            return true;
        }

        var player = getPlayerFromSender(sender);

        if ("confirm".equalsIgnoreCase(args[0])) {
            if (!confirm.containsKey(player.getUniqueId())) {
                messageSender().sendError(sender, "Nothing to confirm");
                return true;
            }
            var name = confirm.get(player.getUniqueId());
            companyData.retrievePlayerCompanyProfile(player)
                    .asFuture()
                    .thenAcceptAsync(optProfile -> {
                        if (optProfile.isEmpty()) {
                            messageSender().sendError(sender, "You are not part of a company");
                            return;
                        }
                        var profile = optProfile.get();
                        if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                            messageSender().sendError(sender, "You are not the owner of the company");
                            return;
                        }

                        var response = economy.withdrawPlayer(player, configuration.companySettings().renamePrice());
                        if (response.type != EconomyResponse.ResponseType.SUCCESS) {
                            messageSender().sendError(sender, "Not enough money");
                            return;
                        }

                        companyData.updateCompanyName(profile, name);
                        messageSender().sendMessage(player, "Company name changed");
                    });
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAcceptAsync(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                        messageSender().sendError(sender, "You are not the owner of the company");
                        return;
                    }

                    if (!economy.has(player, configuration.companySettings().renamePrice())) {
                        messageSender().sendError(sender, "You dont have enought money");
                        return;
                    }

                    var name = String.join(" ", args);
                    confirm.put(player.getUniqueId(), name);
                    var message = MessageComposer.create()
                            .localeCode("Confirm rename",
                                    Replacement.create("name", name), Replacement.create("price", configuration.companySettings().renamePrice()))
                            .text("<click:run_command:/company rename confirm>[").localeCode("confirm").text("]</click>");
                    audiences.sender(player).sendMessage(miniMessage.parse(message.buildLocalized(localizer())));
                    delayedActions.schedule(() -> expireConfirm(player.getUniqueId()), 30 * 20);
                });
        return true;
    }

    private void expireConfirm(UUID uuid) {
        if (confirm.remove(uuid) == null) return;

        var player = getPlugin().getServer().getOfflinePlayer(uuid);
        if (!player.isOnline()) return;

        messageSender().sendMessage(player.getPlayer(), "Confirm for name change expired.");
    }
}
