package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;

public class Deliver extends AdvancedCommand implements IPlayerTabExecutor {
    private final Economy economy;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Info info;
    private final IMessageBlockerService messageBlocker;

    public Deliver(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Info info, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("deliver")
                .addArgument("words.id", true)
                .addArgument("words.material", true)
                .addArgument("words.amount", true)
                .build());
        this.orderData = orderData;
        this.economy = economy;
        this.companyData = companyData;
        this.info = info;
        this.messageBlocker = messageBlocker;
    }

    private void handleFullOrder(Player player, Material material, int amount, CompanyProfile company, FullOrder order) {
        var optContent = order.content(material);
        if (optContent.isEmpty()) {
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.invalidMaterial");
            return;
        }

        var content = optContent.get();
        var deliver = Math.min(amount, content.missing());
        var inv = player.getInventory();
        var slots = inv.all(content.stack().getType());
        var contained = 0;
        for (var entry : slots.entrySet()) {
            var stack = entry.getValue();
            if (!stack.isSimilar(content.stack())) continue;
            var take = Math.min(stack.getAmount(), deliver - contained);
            stack.setAmount(stack.getAmount() - take);
            contained += take;
            if (contained == deliver) {
                break;
            }
        }

        orderData.submitDelivery(player, order, material, contained)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                })
                .thenAccept((v) -> {
                    var refreshedOrder = orderData.retrieveFullOrder(order).join();
                    if (refreshedOrder.isDone()) {
                        messageBlocker.unblockPlayer(player).thenRun(() -> orderDone(refreshedOrder, company));
                        return;
                    }
                    info.renderOrder(player, company.member(player).get(), refreshedOrder);
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                })
        ;
    }

    private void orderDone(FullOrder order, CompanyProfile profile) {
        var payments = order.payments(PaymentType.STACK);
        orderData.submitOrderDelivered(order)
                .asFuture()
                .thenRun(() -> plugin().getServer().getPluginManager().callEvent(new OrderDoneEvent(order, profile)));
        CompletableBukkitFuture.runAsync(() -> {
            for (var entry : payments.entrySet()) {
                var player = plugin().getServer().getOfflinePlayer(entry.getKey());
                var event = new OrderPaymentEvent(order, player, entry.getValue());
                plugin().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) continue;
                economy.depositPlayer(player, entry.getValue());
            }
        });
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);
        var material = arguments.asMaterial(1);
        var amount = "max".equalsIgnoreCase(arguments.asString(2)) ? Integer.MAX_VALUE : arguments.asInt(2);

        CommandAssertions.range(amount, 1, Integer.MAX_VALUE);

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    var optOrder = orderData.retrieveCompanyOrderById(id, company.get().id()).join();
                    if (optOrder.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.unkownOrder");
                        return;
                    }
                    orderData.retrieveFullOrder(optOrder.get())
                            .whenComplete(fullOrder -> {
                                // This part has to be synced to the mainthread
                                handleFullOrder(player, material, amount, company.get(), fullOrder);
                            });
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
