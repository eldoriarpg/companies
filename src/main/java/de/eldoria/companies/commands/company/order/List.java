package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class List extends EldoCommand {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audience;
    private final Economy economy;

    public List(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy) {
        super(plugin);
        this.companyData = companyData;
        audience = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);
        companyData.retrievePlayerCompany(player).asFuture()
                .thenAcceptAsync(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company");
                        return;
                    }
                    orderData.retrieveOrdersByCompany(company.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .asFuture()
                            .thenApplyAsync(orderData::retrieveFullOrders)
                            .thenAcceptAsync(future -> future.whenComplete(orders -> {
                                var component = Component.text()
                                        .append(Component.text("Company orders:"))
                                        .append(Component.newline());
                                for (var order : orders) {
                                    component.append(order.companyShortInfo(localizer(), economy));
                                }
                                audience.sender(sender).sendMessage(component);
                            }));
                });
        return true;
    }
}
