package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Receive extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final IMessageBlockerService messageBlocker;

    public Receive(Plugin plugin, AOrderData orderData, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("receive").addArgument("id", true).build());
        this.orderData = orderData;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);

        orderData.retrieveOrderById(id)
                .whenComplete(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR,player, "error.unkownOrder");
                        return;
                    }

                    var simpleOrder = optOrder.get();
                    if (!simpleOrder.owner().equals(player.getUniqueId())) {
                        messageSender().sendLocalizedError(player, "error.notYourOrder");
                        return;
                    }
                    if (simpleOrder.state() != OrderState.DELIVERED) {
                        messageSender().sendLocalizedError(player, "Not ready");
                        return;
                    }
                    orderData.retrieveFullOrder(optOrder.get())
                            .whenComplete(fullOrder -> {
                                var stacks = fullOrder.createStacks();
                                var empty = 0;
                                for (var content : player.getInventory().getContents()) {
                                    if (content == null) empty++;
                                }

                                if (stacks.size() > empty) {
                                    messageSender().sendLocalizedError(player, "order.receive.inventoryFull",
                                            Replacement.create("amount", stacks.size()));
                                    return;
                                }
                                messageBlocker.unblockPlayer(player).thenRun(() -> {
                                    player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
                                    orderData.submitOrderStateUpdate(fullOrder, OrderState.RECEIVED);
                                    messageSender().sendLocalizedMessage(player, "order.receive.received");
                                });
                            });
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
