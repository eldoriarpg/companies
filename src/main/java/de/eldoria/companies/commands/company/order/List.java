package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
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

import java.util.Collections;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audience;
    private final Economy economy;

    public List(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy) {
        super(plugin, CommandMeta.builder("list").build());
        this.companyData = companyData;
        audience = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.economy = economy;
    }

    public void showOrders(SimpleCompany company, Player player) {
        showOrders(company, player, () -> {
        });
    }

    public void showOrders(SimpleCompany company, Player player, Runnable runnable) {
        orderData.retrieveOrdersByCompany(company, OrderState.CLAIMED, OrderState.CLAIMED)
                .asFuture()
                .thenApply(orderData::retrieveFullOrders)
                .thenAccept(future -> future.whenComplete(orders -> {
                    var component = Component.text()
                            .append(Component.text("Company orders:")).append(Component.space()).append(Component.text("[search]").clickEvent(ClickEvent.runCommand("/company order search query")))
                            .append(Component.newline());
                    for (var order : orders) {
                        component.append(order.companyShortInfo(localizer(), economy)).append(Component.newline());
                    }
                    audience.sender(player).sendMessage(component);
                    runnable.run();
                }));

    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        companyData.retrievePlayerCompany(player).asFuture()
                .thenAcceptAsync(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    showOrders(company.get(), player);
                });
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
