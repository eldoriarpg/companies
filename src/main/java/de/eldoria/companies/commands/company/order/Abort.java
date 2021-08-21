package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Abort extends EldoCommand {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Map<UUID, SimpleOrder> cancel = new HashMap<>();
    private final BukkitAudiences audiences;
    private final List list;

    public Abort(Plugin plugin, ACompanyData companyData, AOrderData orderData, List list) {
        super(plugin);
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.list = list;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<id>")) return true;

        var player = getPlayerFromSender(sender);
        if ("confirm".equalsIgnoreCase(args[0])) {
            var remove = cancel.remove(player.getUniqueId());
            if (remove == null) {
                messageSender().sendError(sender, "Nothing to confirm");
                return true;
            }

            companyData.retrievePlayerCompanyProfile(player)
                    .whenComplete(optProfile -> {
                        if (optProfile.isEmpty()) {
                            messageSender().sendError(player, "You are not part of a Company");
                            return;
                        }
                        var profile = optProfile.get();
                        if (!profile.member(player).get().hasPermissions(CompanyPermission.MANAGE_ORDERS)) {
                            messageSender().sendError(player, "You don't have the permission.");
                            return;
                        }

                        orderData.retrieveOrderById(remove.id())
                                .whenComplete(optOrder -> {
                                    if (optOrder.isEmpty()) {
                                        messageSender().sendError(player, "This order does not exist");
                                        return;
                                    }

                                    var order = optOrder.get();
                                    if (order.company() != profile.id()) {
                                        messageSender().sendError(player, "This order does not belong to your company.");
                                        return;
                                    }

                                    orderData.submitUnclaimOrder(remove).whenComplete(r -> {
                                        list.showOrders(SimpleCompany.forId(remove.company()), player, () ->
                                                getPlugin().getServer().getPluginManager().callEvent(new OrderCanceledEvent(remove, profile)));
                                    });

                                });
                    });
            return true;
        }

        var optId = Parser.parseInt(args[0]);
        if (optId.isEmpty() || optId.getAsInt() < 0) {
            messageSender().sendError(sender, "Invalid number");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optCompany -> {
                    handleCompany(optCompany, player, optId.getAsInt());
                });
        return true;
    }

    private void handleCompany(Optional<CompanyProfile> optCompany, Player player, int id) {
        if (optCompany.isEmpty()) {
            messageSender().sendError(player, "You are not part of a company.");
            return;
        }

        var company = optCompany.get();

        orderData.retrieveOrderById(id)
                .whenComplete(optOrder -> {
                    handleOrder(player, company, optOrder);
                });
    }

    private void handleOrder(Player player, CompanyProfile company, Optional<SimpleOrder> optOrder) {
        if (optOrder.isEmpty()) {
            messageSender().sendError(player, "Unkown order");
            return;
        }
        var order = optOrder.get();

        if (order.company() != company.id()) {
            messageSender().sendError(player, "This order is not owned by your company");
            return;
        }

        if (!company.member(player).get().hasPermission(CompanyPermission.MANAGE_ORDERS)) {
            messageSender().sendError(player, "You are not allowed to cancel orders.");
            return;
        }

        var component = Component.text().append(Component.text("Please confirm the deletion. All already delivered items will be lost."))
                .append(Component.space())
                .append(Component.text("[Confirm]").clickEvent(ClickEvent.runCommand("/company order abort confirm"))).build();
        cancel.put(player.getUniqueId(), order);
        audiences.sender(player).sendMessage(component);
    }
}
