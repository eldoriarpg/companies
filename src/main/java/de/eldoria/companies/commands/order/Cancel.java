package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandMetaBuilder;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class Cancel extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final List list;

    public Cancel(Plugin plugin, AOrderData orderData, Economy economy, List list) {
        super(plugin, new CommandMetaBuilder("cancel")
                .addArgument("words.id", true)
                .build());
        this.orderData = orderData;
        this.economy = economy;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);

        orderData.retrieveOrderById(id)
                .asFuture()
                .whenComplete((optOrder, err) -> {
                    if (err != null) {
                        plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                        return;
                    }
                    if (optOrder.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.SUBTITLE, MessageType.ERROR,sender, "error.unkownOrder");
                        return;
                    }

                    var simpleOrder = optOrder.get();
                    var player = getPlayerFromSender(sender);
                    if (!simpleOrder.owner().equals(player.getUniqueId())) {
                        messageSender().sendLocalizedError(sender, "error.notYourOrder");
                        return;
                    }
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messageSender().sendLocalizedError(sender, "error.orderAlreadyClaimed");
                        return;
                    }

                    var fullOrder = orderData.retrieveFullOrder(optOrder.get()).join();
                    CompletableFuture.runAsync(() -> economy.depositPlayer(player, fullOrder.price()));
                    orderData.submitOrderDeletion(fullOrder).join();
                    list.showOrders(player, () -> {
                        messageSender().sendLocalizedMessage(sender, "order.cancel.canceled",
                                Replacement.create("money", economy.format(fullOrder.price())));
                    });
                });
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
