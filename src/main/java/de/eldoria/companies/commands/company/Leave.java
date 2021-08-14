package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Leave extends EldoCommand {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Set<UUID> leaves = new HashSet<>();
    private final BukkitAudiences audiences;

    public Leave(Plugin plugin, ACompanyData companyData, AOrderData orderData) {
        super(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);

        if (args.length != 0 && "confirm".equalsIgnoreCase(args[0])) {
            if (leaves.remove(player.getUniqueId())) {
                leave(player);
                return true;
            }
            messageSender().sendError(sender, "Nothing to confirm");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company");
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
        return true;
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
                        for (var member : profile.members()) {
                            if (member.player().isOnline()) {
                                messageSender().sendMessage(member.player().getPlayer(), "Your company was disbanded.");
                            }
                        }
                        return;
                    }
                    for (var member : profile.members()) {
                        if (member.player().getUniqueId().equals(player.getUniqueId())) continue;
                        if (member.player().isOnline()) {
                            messageSender().sendMessage(member.player().getPlayer(), player.getName() + " has left the company.");
                        }
                    }
                    companyData.submitMemberUpdate(profile.member(player).get().kick());
                    messageSender().sendMessage(player, "You left the company.");
                });

    }
}
