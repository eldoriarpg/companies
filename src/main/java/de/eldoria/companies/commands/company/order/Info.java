package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;

public class Info extends EldoCommand {
    private final AOrderData orderData;
    private final BukkitAudiences audiences;
    private final ACompanyData companyData;
    private final Economy economy;

    public Info(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy) {
        super(plugin);
        audiences = BukkitAudiences.create(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        this.economy = economy;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<id>")) return true;
        var player = getPlayerFromSender(sender);
        var optId = Parser.parseInt(args[0]);
        if (optId.isEmpty()) {
            messageSender().sendError(sender, "Invalid id");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player).asFuture()
                .thenAccept(company -> {
                    handleCompany(sender, player, optId, company);
                });
        return true;
    }

    private void handleCompany(@NotNull CommandSender sender, Player player, OptionalInt optId, Optional<CompanyProfile> company) {
        if (company.isEmpty()) {
            messageSender().sendError(sender, "You are not part of a company");
            return;
        }
        orderData.retrieveOrderById(optId.getAsInt())
                .whenComplete(order -> {
                    handleOrder(sender, player, company, order);
                });
    }

    private void handleOrder(@NotNull CommandSender sender, Player player, Optional<CompanyProfile> company, Optional<SimpleOrder> order) {
        if (order.isPresent()) {
            orderData.retrieveFullOrder(order.get())
                    .whenComplete(fullOrder -> {
                        var component = fullOrder.companyDetailInfo(company.get().member(player).get(), localizer(), economy);
                        audiences.sender(sender).sendMessage(component);
                    });
            return;
        }
        messageSender().sendError(sender, "Order not found.");
    }
}
