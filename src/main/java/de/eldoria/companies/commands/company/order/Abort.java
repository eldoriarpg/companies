package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class Abort extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Map<UUID, SimpleOrder> cancel = new HashMap<>();
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final List list;


    public Abort(Plugin plugin, ACompanyData companyData, AOrderData orderData, List list) {
        super(plugin, CommandMeta.builder("abort")
                .addArgument("id", true)
                .build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        this.orderData = orderData;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            if (confirm(player)) return;
        }

        var id = arguments.asInt(0);

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optCompany, err) -> {
                    if (err != null) {
                        plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                        return;
                    }
                    if (optCompany.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company.");
                        return;
                    }

                    var company = optCompany.get();

                    var optOrder = orderData.retrieveOrderById(id).join();
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

                    var composer = MessageComposer.create().text("<%s>", Colors.NEUTRAL).localeCode("Please confirm the deletion. All already delivered items will be lost.")
                            .text("<click:run_command:/company order abort confirm><%s>[", Colors.REMOVE).localeCode("confirm").text("]</click>");
                    cancel.put(player.getUniqueId(), order);
                    audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
                });
    }

    private boolean confirm(@NotNull Player player) {
        var remove = cancel.remove(player.getUniqueId());
        if (remove == null) {
            messageSender().sendError(player, "Nothing to confirm");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optProfile, err) -> {
                    if (err != null) {
                        plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                        return;
                    }

                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a Company");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.MANAGE_ORDERS)) {
                        messageSender().sendError(player, "You don't have the permission.");
                        return;
                    }

                    var optOrder = orderData.retrieveOrderById(remove.id()).join();
                    if (optOrder.isEmpty()) {
                        messageSender().sendError(player, "This order does not exist");
                        return;
                    }

                    var order = optOrder.get();
                    if (order.company() != profile.id()) {
                        messageSender().sendError(player, "This order does not belong to your company.");
                        return;
                    }

                    orderData.submitUnclaimOrder(remove).join();

                    list.showOrders(SimpleCompany.forId(remove.company()), player, () ->
                            plugin().getServer().getPluginManager().callEvent(new OrderCanceledEvent(remove, profile)));
                });
        return false;
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
