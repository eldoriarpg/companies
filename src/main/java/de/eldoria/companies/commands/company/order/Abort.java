package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
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
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
                .addArgument("words.id", true)
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
                    var order = checkOrder(err, optCompany, player, id);
                    if(order.isEmpty()){
                        return;
                    }

                    var composer = MessageComposer.create().text("<%s>", Colors.NEUTRAL).localeCode("company.order.abort.confirm")
                            .text("<click:run_command:/company order abort confirm><%s>[", Colors.REMOVE).localeCode("words.confirm").text("]</click>");
                    cancel.put(player.getUniqueId(), order.get());
                    audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
                });
    }

    private boolean confirm(@NotNull Player player) {
        var remove = cancel.remove(player.getUniqueId());
        if (remove == null) {
            messageSender().sendLocalized(MessageChannel.SUBTITLE, MessageType.ERROR,player, "error.noConfirm");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optCompany, err) -> {
                    var id = remove.id();
                    if(checkOrder(err, optCompany, player, id).isEmpty()){
                        return;
                    }

                    orderData.submitUnclaimOrder(remove).join();

                    list.showOrders(SimpleCompany.forId(remove.company()), player, () ->
                            plugin().getServer().getPluginManager().callEvent(new OrderCanceledEvent(remove, optCompany.get())));
                });
        return false;
    }

    private Optional<SimpleOrder> checkOrder(Throwable err, Optional<CompanyProfile> optCompany, Player player, int id){
        if (err != null) {
            plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
            return Optional.empty();
        }

        if (optCompany.isEmpty()) {
            messageSender().sendLocalized(MessageChannel.SUBTITLE, MessageType.ERROR,player, "error.noMember");
            return Optional.empty();
        }

        var company = optCompany.get();

        var optOrder = orderData.retrieveOrderById(id).join();
        if (optOrder.isEmpty()) {
            messageSender().sendLocalized(MessageChannel.SUBTITLE, MessageType.ERROR,player, "error.unkownOrder");
            return Optional.empty();
        }

        var order = optOrder.get();
        if (order.company() != company.id()) {
            messageSender().sendLocalized(MessageChannel.SUBTITLE, MessageType.ERROR,player, "error.orderNotOwned");
            return Optional.empty();
        }

        if (!company.member(player).get().hasPermissions(CompanyPermission.MANAGE_ORDERS)) {
            messageSender().sendLocalized(MessageChannel.SUBTITLE, MessageType.ERROR,player, "error.permission.cancelOrder");
            return Optional.empty();
        }
        return optOrder;
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
