package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;

public class Deliver extends EldoCommand {
    private final Economy economy;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audiences;

    public Deliver(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy) {
        super(plugin);
        this.orderData = orderData;
        this.economy = economy;
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 3, "<id> <material> <amount>")) return true;

        var player = getPlayerFromSender(sender);
        var optId = Parser.parseInt(args[0]);
        if (optId.isEmpty()) {
            messageSender().sendError(sender, "Invalid id");
            return true;
        }

        var material = EnumUtil.parse(args[1], Material.class);
        if (material == null) {
            messageSender().sendError(sender, "Invalid material");
            return true;
        }
        var optAmount = OptionalInt.empty();
        if ("max".equalsIgnoreCase(args[2])) {
            optAmount = OptionalInt.of(Integer.MAX_VALUE);
        }
        if (optAmount.isEmpty()) {
            optAmount = Parser.parseInt(args[2]);
        }

        if (optAmount.isEmpty() || (optAmount.getAsInt() < 1)) {
            messageSender().sendError(sender, "Invalid amount");
            return true;
        }
        var id = optId.getAsInt();
        var amount = optAmount.getAsInt();

        companyData.retrievePlayerCompanyProfile(player).asFuture()
                .thenAccept(company -> {
                    handleCompany(sender, player, material, id, amount, company);
                });
        return true;
    }

    private void handleCompany(@NotNull CommandSender sender, Player player, Material material, int id, int amount, Optional<CompanyProfile> company) {
        if (company.isEmpty()) {
            messageSender().sendError(sender, "You are not part of a company");
            return;
        }
        orderData.retrieveCompanyOrderById(id, company.get().id())
                .whenComplete(simpleOrder -> {
                    handleOrder(sender, player, material, amount, company, simpleOrder);
                });
    }

    private void handleOrder(@NotNull CommandSender sender, Player player, Material material, int amount, Optional<CompanyProfile> company, Optional<SimpleOrder> simpleOrder) {
        if (simpleOrder.isEmpty()) {
            messageSender().sendError(sender, "Order not found.");
            return;
        }
        orderData.retrieveFullOrder(simpleOrder.get())
                .whenComplete(fullOrder -> {
                    handleFullOrder(sender, player, material, amount, company, simpleOrder, fullOrder);
                });
    }

    private void handleFullOrder(@NotNull CommandSender sender, Player player, Material material, int amount, Optional<CompanyProfile> company, Optional<SimpleOrder> simpleOrder, FullOrder fullOrder) {
        var optContent = fullOrder.content(material);
        if (optContent.isEmpty()) {
            messageSender().sendError(sender, "Invalid material");
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
        orderData.submitDelivery(player, simpleOrder.get(), material, contained)
                .whenComplete(v -> {
                    orderData.retrieveFullOrder(simpleOrder.get())
                            .whenComplete(refreshedFullOrder -> {
                                handleRefreshedOrder(sender, player, company, refreshedFullOrder);
                            });
                });
    }

    private void handleRefreshedOrder(@NotNull CommandSender sender, Player player, Optional<CompanyProfile> company, FullOrder refreshedFullOrder) {
        if (refreshedFullOrder.isDone()) {
            orderDone(refreshedFullOrder);
            return;
        }
        audiences.sender(sender).sendMessage(refreshedFullOrder.companyDetailInfo(company.get().member(player).get(), localizer(), economy));
    }

    private void orderDone(FullOrder order) {
        var payments = order.payments(PaymentType.STACK);
        orderData.submitOrderDelivered(order);
        CompletableBukkitFuture.runAsync(() -> {
            for (var entry : payments.entrySet()) {
                var player = getPlugin().getServer().getOfflinePlayer(entry.getKey());
                economy.depositPlayer(player, entry.getValue());
                if (player.isOnline()) {
                    messageSender().sendMessage(player.getPlayer(), "Order \" " + order.name() + "\" delivered. You received " + entry.getValue());
                }
            }
            var owner = getPlugin().getServer().getOfflinePlayer(order.owner());
            if (owner.isOnline()) {
                Component.text().append(Component.text("Order delivered.").append(Component.space()).append(Component.text("[Click here to claim]"))
                        .clickEvent(ClickEvent.runCommand("/order receive " + order.id())));
            }
        });
    }
}
