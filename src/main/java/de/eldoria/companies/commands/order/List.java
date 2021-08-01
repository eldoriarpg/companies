package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.CompanyData;
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
    private final CompanyData companyData;
    private Economy economy;
    private BukkitAudiences audiences;

    public List(Plugin plugin, CompanyData companyData, Economy economy) {
        super(plugin);
        this.companyData = companyData;
        this.economy = economy;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) {
            return true;
        }

        companyData.retrieveOrdersByPlayer(getPlayerFromSender(sender), OrderState.UNCLAIMED, OrderState.DELIVERED).asFuture()
                .thenApply(companyData::retrieveFullOrders)
                .whenComplete(((future, e) -> future
                        .whenComplete(orders -> {
                            var component = Component.text()
                                    .append(Component.text("Your orders:"))
                                    .append(Component.newline());
                            for (var order : orders) {
                                component.append(order.userShortInfo(localizer(), economy));
                            }
                            audiences.sender(sender).sendMessage(component);
                        })));
        return true;
    }
}
