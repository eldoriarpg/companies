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
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audience;
    private final MiniMessage miniMessage;
    private final Economy economy;

    public List(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy) {
        super(plugin, CommandMeta.builder("list").build());
        this.companyData = companyData;
        audience = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
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
                    var builder = MessageComposer.create()
                            .localeCode("Company orders").text(": <click:run_command:/company order search query>[").localeCode("search").text("]</click>").newLine();
                    for (var order : orders) {
                        builder.text(order.companyShortInfo(economy)).newLine();
                    }
                    audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
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
