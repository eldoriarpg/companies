package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Accept extends EldoCommand {
    private final CompanyData companyData;
    private final OrderData orderData;
    private final Configuration configuration;

    public Accept(Plugin plugin, CompanyData companyData, OrderData orderData, Configuration configuration) {
        super(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        this.configuration = configuration;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<id>")) return true;

        var optId = Parser.parseInt(args[0]);
        if (optId.isEmpty() || optId.getAsInt() < 0) {
            messageSender().sendError(sender, "Invalid number");
            return true;
        }

        var player = getPlayerFromSender(sender);

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company");
                        return;
                    }
                    var profile = optProfile.get();
                    var companyMember = profile.member(player).get();
                    if (!companyMember.hasPermission(CompanyPermission.ACCEPT_ORDER)) {
                        messageSender().sendError(sender, "You are not allowed to accept orders.");
                        return;
                    }

                    orderData.retrieveCompanyOrderCount(profile)
                            .whenComplete(count -> {
                                if (count >= configuration.companySettings().maxOrders()) {
                                    messageSender().sendError(sender, "Maximum order limit reached.");
                                    return;
                                }
                                orderData.retrieveOrderById(optId.getAsInt())
                                        .whenComplete(order -> {
                                            if (order.isEmpty()) {
                                                messageSender().sendError(sender, "Unknown order");
                                            }
                                            var simpleOrder = order.get();
                                            if (simpleOrder.state() != OrderState.UNCLAIMED) {
                                                messageSender().sendError(sender, "This order is not claimable");
                                                return;
                                            }
                                            orderData.submitOrderClaim(profile, simpleOrder).whenComplete(success ->{
                                                if(success){
                                                    messageSender().sendMessage(sender, "Order claimed.");
                                                }else {
                                                    messageSender().sendError(sender, "Order could not be claimed");
                                                }
                                            });
                                        });
                            });
                });
        return true;
    }
}
