package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Leave extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Set<UUID> leaves = new HashSet<>();
    private final BukkitAudiences audiences;

    public Leave(Plugin plugin, ACompanyData companyData, AOrderData orderData) {
        super(plugin,
                CommandMeta.builder("leave")
                        .build());
        this.companyData = companyData;
        this.orderData = orderData;
        audiences = BukkitAudiences.create(plugin);
    }

    public void leave(Player player) {
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    if (profile.member(player).get().isOwner()) {
                        companyData.submitCompanyPurge(profile);
                        orderData.submitCompanyOrdersPurge(profile);
                        messageSender().sendMessage(player, "The company was disbanded");
                        plugin().getServer().getPluginManager().callEvent(new CompanyDisbandEvent(optProfile.get()));
                        return;
                    }
                    plugin().getServer().getPluginManager().callEvent(new CompanyLeaveEvent(optProfile.get(), player));
                    companyData.submitMemberUpdate(profile.member(player).get().kick());
                    messageSender().sendMessage(player, "You left the company.");
                });
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if (!arguments.isEmpty() && "confirm".equalsIgnoreCase(arguments.asString(0))) {
            if (leaves.remove(player.getUniqueId())) {
                leave(player);
                return;
            }
            messageSender().sendError(player, "Nothing to confirm");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    leaves.add(player.getUniqueId());
                    TextComponent message;
                    if (profile.member(player).get().isOwner()) {
                        message = Component.text()
                                .append(Component.text("If you leave the company it will be dissolved. Please confirm."))
                                .append(Component.text("[Confirm]").clickEvent(ClickEvent.runCommand("/company leave confirm")))
                                .build();
                    } else {
                        message = Component.text()
                                .append(Component.text("Please confirm that you want to leave the company."))
                                .append(Component.text("[Confirm]").clickEvent(ClickEvent.runCommand("/company leave confirm")))
                                .build();
                    }
                    audiences.player(player).sendMessage(message);
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        if (leaves.contains(player.getUniqueId())) {
            return TabCompleteUtil.complete(arguments.asString(0), "confirm");
        }
        return Collections.emptyList();
    }
}
