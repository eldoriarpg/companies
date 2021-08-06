package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.permissions.CompanyPermissions;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Abort extends EldoCommand {
    private final CompanyData companyData;
    private final Map<UUID, SimpleOrder> cancel = new HashMap<>();
    private final BukkitAudiences audiences;

    public Abort(Plugin plugin, CompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
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
            companyData.submitUnclaimOrder(remove);
            messageSender().sendMessage(sender, "Order canceled.");
            return true;
        }

        var optId = Parser.parseInt(args[0]);
        if (optId.isEmpty() || optId.getAsInt() < 0) {
            messageSender().sendError(sender, "Invalid number");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optCompand -> {
                    if (optCompand.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company.");
                        return;
                    }

                    var company = optCompand.get();

                    companyData.retrieveOrderById(optId.getAsInt())
                            .whenComplete(optOrder -> {
                                if (optOrder.isEmpty()) {
                                    messageSender().sendError(sender, "Unkown order");
                                    return;
                                }
                                var order = optOrder.get();

                                if (!company.member(player).get().hasPermission(CompanyPermissions.ACCEPT_ORDER)) {
                                    messageSender().sendError(sender, "You are not allowed to cancel orders.");
                                    return;
                                }

                                if (order.company() != company.id()) {
                                    messageSender().sendError(sender, "This order is not owned by your company");
                                    return;
                                }

                                var component = Component.text().append(Component.text("Please confirm the deletion. All already delivered items will be lost."))
                                        .append(Component.space())
                                        .append(Component.text("[Confirm]").clickEvent(ClickEvent.runCommand("/company order abort confirm"))).build();

                                audiences.sender(player).sendMessage(component);
                            });
                });
        return true;
    }
}
