package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Level;

public class Accept extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Configuration configuration;
    private final IMessageBlockerService messageBlocker;

    public Accept(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("accept")
                .addArgument("id", true)
                .build());
        this.companyData = companyData;
        this.orderData = orderData;
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);
        CommandAssertions.min(id, 0);

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optProfile, err) -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    var companyMember = profile.member(player).get();
                    if (!companyMember.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                        messageSender().sendError(player, "You are not allowed to accept orders.");
                        return;
                    }

                    var count = orderData.retrieveCompanyOrderCount(profile).join();
                    if (count >= configuration.companySettings().level(profile.level()).orElse(new CompanyLevel()).settings().maxOrders()) {
                        messageSender().sendError(player, "Maximum order limit reached.");
                        return;
                    }

                    var optOrder = orderData.retrieveOrderById(id).join();
                    if (optOrder.isEmpty()) {
                        messageSender().sendError(player, "Unknown order");
                    }

                    var simpleOrder = optOrder.get();
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messageSender().sendError(player, "This order is not claimable");
                        return;
                    }

                    if (orderData.submitOrderClaim(profile, simpleOrder).join()) {
                        messageBlocker.unblockPlayer(player).thenRun(() -> {
                            player.getServer().getPluginManager().callEvent(new OrderAcceptEvent(simpleOrder, profile));
                        });
                        return;
                    }
                    messageSender().sendError(player, "Order could not be claimed");
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
