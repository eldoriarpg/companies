package de.eldoria.companies.commands.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
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
import org.jetbrains.annotations.Nullable;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final BukkitAudiences audiences;
    private final Configuration configuration;

    public List(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin, CommandMeta.builder("list").build());
        this.orderData = orderData;
        this.economy = economy;
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
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
                            if (configuration.userSettings().maxOrders() > orders.size()) {
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

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        showOrders(player);
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
