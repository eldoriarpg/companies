package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final BukkitAudiences audiences;
    private final ACompanyData companyData;
    private final Economy economy;
    private final Configuration configuration;

    public Info(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin, CommandMeta.builder("info")
                .addArgument("id", true)
                .build());
        audiences = BukkitAudiences.create(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        this.economy = economy;
        this.configuration = configuration;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    var optOrder = orderData.retrieveOrderById(id).join();
                    if (optOrder.isEmpty() || optOrder.get().company() != profile.id()) {
                        messageSender().sendError(player, "Order not found.");
                        return;
                    }
                    var order = optOrder.get();
                    var fullOrder = orderData.retrieveFullOrder(order).join();
                    var component = fullOrder.companyDetailInfo(profile.member(player).get(), configuration, localizer(), economy);
                    audiences.sender(player).sendMessage(component);
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
