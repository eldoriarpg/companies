package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final MessageBlocker messageBlocker;

    public Info(Plugin plugin, AOrderData orderData, Economy economy, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("info").addArgument("id", true).build());
        this.orderData = orderData;
        this.economy = economy;
        this.audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.miniMessage();
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args) throws CommandException {
        var id = args.asInt(0);

        orderData.retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept((order) -> {
                    if (order.isPresent()) {
                        messageBlocker.blockPlayer(player);
                        var fullOrder = orderData.retrieveFullOrder(order.get()).join();
                        var builder = MessageComposer.create().text(fullOrder.userDetailInfo(economy));
                        if (messageBlocker.isBlocked(player)) {
                            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                        }
                        messageBlocker.announce(player, "[x]");
                        builder.prependLines(25);
                        audiences.sender(player).sendMessage(miniMessage.deserialize(localizer().localize(builder.build())));
                        return;
                    }
                    messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.unkownOrder");
                });
    }
}
