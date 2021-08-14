package de.eldoria.companies.commands.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class List extends EldoCommand {
    private final AOrderData orderData;
    private final Economy economy;
    private final BukkitAudiences audiences;
    private final Configuration configuration;

    public List(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        this.orderData = orderData;
        this.economy = economy;
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        showOrders(getPlayerFromSender(sender));
        return true;
    }

    public void showOrders(Player player) {
        showOrders(player, () -> {
        });
    }

    public void showOrders(Player player, Runnable whenComplete) {
        orderData.retrieveOrdersByPlayer(player, OrderState.UNCLAIMED, OrderState.DELIVERED).asFuture()
                .thenApplyAsync(orderData::retrieveFullOrders)
                .thenAcceptAsync((future -> future
                        .whenComplete(orders -> {
                            var builder = Component.text()
                                    .append(Component.text("Your orders:"));
                            if(configuration.userSettings().maxOrders() > orders.size()){
                                builder.append(Component.text("[New Order]").clickEvent(ClickEvent.suggestCommand("/order create ")));
                            }
                            builder.append(Component.newline());
                            for (var order : orders) {
                                builder.append(order.userShortInfo(localizer(), economy)).append(Component.newline());
                            }
                            audiences.player(player).sendMessage(builder);
                            whenComplete.run();
                        })));

    }
}
