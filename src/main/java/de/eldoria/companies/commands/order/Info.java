package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandMetaBuilder;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final BukkitAudiences audiences;

    public Info(Plugin plugin, AOrderData orderData, Economy economy) {
        super(plugin, CommandMeta.builder("info").addArgument("id", true).build());
        this.orderData = orderData;
        this.economy = economy;
        this.audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args) throws CommandException {
        var id = args.asInt(0);

        orderData.retrieveOrderById(id)
                .whenComplete(order -> {
                    if (order.isPresent()) {
                        orderData.retrieveFullOrder(order.get())
                                .whenComplete(fullOrder -> audiences.sender(player)
                                        .sendMessage(fullOrder.userDetailInfo(localizer(), economy)));
                        return;
                    }
                    messageSender().sendError(player, "Order not found.");
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
