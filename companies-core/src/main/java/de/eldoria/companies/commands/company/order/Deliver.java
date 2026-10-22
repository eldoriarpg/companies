/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class Deliver extends AdvancedCommand implements IPlayerTabExecutor {
    private final Economy economy;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Info info;
    private final MessageBlocker messageBlocker;

    public Deliver(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Info info, MessageBlocker messageBlocker) {
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

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);
        var material = arguments.asMaterial(1);
        var amount = "max".equalsIgnoreCase(arguments.asString(2)) ? Integer.MAX_VALUE : arguments.asInt(2);

        CommandAssertions.range(amount, 1, Integer.MAX_VALUE);

        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(company -> {
                       if (company.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }
                       var optOrder = orderData.retrieveCompanyOrderById(id, company.get()
                                                                                    .id())
                                               .join();
                       if (optOrder.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.unkownOrder");
                           return;
                       }
                       if (optOrder.get().state() != OrderState.CLAIMED) {
                           messageSender().sendErrorActionBar(player, "error.orderNotActive");
                           return;
                       }
                       orderData.retrieveFullOrder(optOrder.get())
                                .whenComplete(fullOrder -> {
                                    // This part has to be synced to the mainthread
                                    handleFullOrder(player, material, amount, company.get(), fullOrder);
                                });
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   });
    }

    private void handleFullOrder(Player player, Material material, int amount, CompanyProfile company, FullOrder order) {
        if (!player.isOnline()) return;
        var optContent = order.content(material);
        if (optContent.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.invalidMaterial");
            return;
        }

        var content = optContent.get();
        var deliver = Math.min(amount, content.missing());
        var inv = player.getInventory();
        var slots = inv.all(content.stack()
                                   .getType());
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

        var removed = contained;
        orderData.submitDelivery(player, order, material, removed)
                 .asFuture()
                 .exceptionally(err -> {
                     plugin().getLogger()
                             .log(Level.SEVERE, "Something went wrong", err);
                     return 0;
                 })
                 .thenAcceptAsync(delivered -> giveBack(player, content.stack(), removed - delivered), Threading.mainThread(plugin()))
                 .thenCompose(v -> orderData.retrieveFullOrder(order)
                                            .asFuture())
                 .thenAccept(refreshedOrder -> {
                     if (refreshedOrder.isDone()) {
                         messageBlocker.unblockPlayer(player)
                                       .thenRun(() -> orderDone(refreshedOrder, company));
                         return;
                     }
                     info.renderOrder(player, company.member(player)
                                                     .get(), refreshedOrder);
                 })
                 .exceptionally(err -> {
                     plugin().getLogger()
                             .log(Level.SEVERE, "Something went wrong", err);
                     return null;
                 })
        ;
    }

    private void giveBack(Player player, ItemStack stack, int amount) {
        if (amount <= 0) return;
        if (player.isOnline()) {
            Inventories.give(player, Inventories.split(stack, amount));
            return;
        }
        orderData.submitItemReturn(player.getUniqueId(), stack, amount);
    }

    private void orderDone(FullOrder order, CompanyProfile profile) {
        orderData.submitOrderDelivered(order)
                 .asFuture()
                 .thenAccept(delivered -> delivered.ifPresent(deliveredOrder -> {
                     plugin().getServer()
                             .getPluginManager()
                             .callEvent(new OrderDoneEvent(deliveredOrder, profile));
                     pay(deliveredOrder);
                 }))
                 .exceptionally(err -> {
                     plugin().getLogger()
                             .log(Level.SEVERE, "Something went wrong", err);
                     return null;
                 });
    }

    private void pay(FullOrder order) {
        Map<OfflinePlayer, Double> payments = new HashMap<>();
        for (var entry : order.payments(PaymentType.STACK).entrySet()) {
            var player = plugin().getServer()
                                 .getOfflinePlayer(entry.getKey());
            var event = new OrderPaymentEvent(order, player, entry.getValue());
            plugin().getServer()
                    .getPluginManager()
                    .callEvent(event);
            if (event.isCancelled()) continue;
            payments.put(player, entry.getValue());
        }
        Threading.mainThread(plugin())
                 .execute(() -> payments.forEach(economy::depositPlayer));
    }
}
