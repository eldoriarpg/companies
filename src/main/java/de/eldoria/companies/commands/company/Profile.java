package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Profile extends EldoCommand {
    private final CompanyData companyData;
    private final OrderData orderData;

    public Profile(Plugin plugin, CompanyData companyData, OrderData orderData) {
        super(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) return true;

        var player = getPlayerFromSender(sender);
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company.");
                        return;
                    }

                    orderData.retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .whenComplete(orders -> {
                                var profile = optProfile.get();
                                Component.text()
                                        .append(Component.text(profile.name()).append(Component.newline()))
                                        .append(Component.text("Founded " + profile.foundedString())).append(Component.newline())
                                        .append(Component.text("Members: " + profile.members().size())).append(Component.text("[list]")
                                                .clickEvent(ClickEvent.runCommand("/company member"))).append(Component.newline())
                                        .append(Component.text("Orders: " + orders.size())).append(Component.text("[list]")
                                                .clickEvent(ClickEvent.runCommand("/company order list")));
                            });
                });
        return true;
    }
}
