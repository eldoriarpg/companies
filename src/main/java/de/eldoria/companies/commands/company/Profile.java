package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class Profile extends EldoCommand {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audiences;

    public Profile(Plugin plugin, ACompanyData companyData, AOrderData orderData) {
        super(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company.");
                        return;
                    }

                    orderData.retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .whenComplete(orders -> {
                                sendProfile(player, optProfile, orders);
                            });
                });
        return true;
    }

    private void sendProfile(Player player, Optional<CompanyProfile> optProfile, List<SimpleOrder> orders) {
        var profile = optProfile.get();
        var component = Component.text()
                .append(Component.text(profile.name()).append(Component.newline()))
                .append(Component.text("Founded " + profile.foundedString())).append(Component.newline())
                .append(Component.text("Leader: " + profile.owner().player().getName())).append(Component.newline())
                .append(Component.text("Members: " + profile.members().size())).append(Component.text("[list]")
                        .clickEvent(ClickEvent.runCommand("/company member"))).append(Component.newline())
                .append(Component.text("Orders: " + orders.size())).append(Component.text("[list]")
                        .clickEvent(ClickEvent.runCommand("/company order list"))).build();
        audiences.player(player).sendMessage(component);
    }
}
