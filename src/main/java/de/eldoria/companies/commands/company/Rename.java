package de.eldoria.companies.commands.company;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
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
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Rename extends AdvancedCommand implements IPlayerTabExecutor {
    private final Map<UUID, String> confirm = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final Economy economy;
    private final ACompanyData companyData;
    private final DelayedActions delayedActions;

    public Rename(Plugin plugin, Configuration configuration, Economy economy, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("rename")
                .addArgument("name", true)
                .build());
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
        this.economy = economy;
        this.companyData = companyData;
        delayedActions = DelayedActions.start(plugin);
    }

    private void expireConfirm(UUID uuid) {
        if (confirm.remove(uuid) == null) return;

        var player = plugin().getServer().getOfflinePlayer(uuid);
        if (!player.isOnline()) return;

        messageSender().sendMessage(player.getPlayer(), "Confirm for name change expired.");
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            if (!confirm.containsKey(player.getUniqueId())) {
                messageSender().sendError(player, "Nothing to confirm");
                return;
            }
            var name = confirm.get(player.getUniqueId());
            companyData.retrievePlayerCompanyProfile(player)
                    // TODO: Check if company name is already taken
                    .asFuture()
                    .thenAcceptAsync(optProfile -> {
                        if (optProfile.isEmpty()) {
                            messageSender().sendError(player, "You are not part of a company");
                            return;
                        }
                        var profile = optProfile.get();
                        if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                            messageSender().sendError(player, "You are not the owner of the company");
                            return;
                        }

                        var response = economy.withdrawPlayer(player, configuration.companySettings().renamePrice());
                        if (response.type != EconomyResponse.ResponseType.SUCCESS) {
                            messageSender().sendError(player, "Not enough money");
                            return;
                        }

                        companyData.updateCompanyName(profile, name);
                        messageSender().sendMessage(player, "Company name changed");
                    });
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAcceptAsync(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                        messageSender().sendError(player, "You are not the owner of the company");
                        return;
                    }

                    if (!economy.has(player, configuration.companySettings().renamePrice())) {
                        messageSender().sendError(player, "You dont have enought money");
                        return;
                    }

                    var name = arguments.join();
                    confirm.put(player.getUniqueId(), name);
                    var composer = MessageComposer.create().text("<%s>", Colors.NEUTRAL).localeCode("Renaming a company costs %AMOUNT%. Do you want rename the company to %NAME%",
                                    Replacement.create("AMOUNT", String.format("<%s>%s<%s>",
                                            Colors.HEADING, economy.format(configuration.companySettings().foudingPrice()), Colors.NEUTRAL)),
                                    Replacement.create("NAME", String.format("<%s>%s<%s>", Colors.HEADING, name, Colors.NEUTRAL)))
                            .newLine()
                            .text("<%s><click:run_command:/company rename confirm><%s>[", Colors.ADD).localeCode("confirm").text("]</click>");
                    audiences.sender(player).sendMessage(miniMessage.parse(composer.buildLocalized(localizer())));
                    delayedActions.schedule(() -> expireConfirm(player.getUniqueId()), 30 * 20);
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
