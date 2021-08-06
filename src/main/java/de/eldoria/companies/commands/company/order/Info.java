package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Info extends EldoCommand {
    private BukkitAudiences audiences;
    private CompanyData companyData;
    private final OrderData orderData;
    private Economy economy;

    public Info(Plugin plugin, CompanyData companyData, OrderData orderData, Economy economy) {
        super(plugin);
        audiences = BukkitAudiences.create(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        this.economy = economy;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<id>")) return true;
        if (denyConsole(sender)) return true;

        var player = getPlayerFromSender(sender);
        var optId = Parser.parseInt(args[0]);
        if (optId.isEmpty()) {
            messageSender().sendError(sender, "Invalid id");
            return true;
        }

        companyData.retrievePlayerCompany(player).asFuture()
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company");
                        return;
                    }
                    orderData.retrieveCompanyOrderById(optId.getAsInt(), company.get().id())
                            .whenComplete(order -> {
                                if (order.isPresent()) {
                                    orderData.retrieveFullOrder(order.get())
                                            .whenComplete(fullOrder -> {
                                                var component = fullOrder.companyDetailInfo(localizer(), economy);
                                                audiences.sender(sender).sendMessage(component);
                                            });
                                    return;
                                }
                                messageSender().sendError(sender, "Order not found.");
                            });
                });
        return true;
    }
}
