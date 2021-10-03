package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.services.messages.IMessageBlockerService;
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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audience;
    private final IMessageBlockerService messageBlocker;
    private final MiniMessage miniMessage;
    private final Economy economy;

    public List(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("list").build());
        this.companyData = companyData;
        audience = BukkitAudiences.create(plugin);
        this.messageBlocker = messageBlocker;
        miniMessage = MiniMessage.get();
        this.orderData = orderData;
        this.economy = economy;
    }

    public void showOrders(ISimpleCompany company, Player player) {
        showOrders(company, player, () -> {
        });
    }

    public void showOrders(ISimpleCompany company, Player player, Runnable runnable) {
        orderData.retrieveOrdersByCompany(company, OrderState.CLAIMED, OrderState.CLAIMED)
                .asFuture().exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Collections.emptyList();
                })

                .thenApply(orders -> orderData.retrieveFullOrders(orders)
                        .asFuture()
                        .exceptionally(err -> {
                            plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                            return Collections.emptyList();
                        })
                        .join())
                .thenAccept(fullOrders -> {
                    messageBlocker.blockPlayer(player);
                    var builder = MessageComposer.create()
                            .text("<%s>", Colors.HEADING).localeCode("company.order.list.orders")
                            .text(": <click:run_command:/company order search query><%s>[", Colors.SHOW)
                            .localeCode("words.search").text("]</click>").newLine();
                    for (var order : fullOrders) {
                        builder.text(order.companyShortInfo(economy)).newLine();
                    }
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    messageBlocker.announce(player, "[x]");
                    builder.prependLines(25);
                    audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
                    runnable.run();
                });

    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        companyData.retrievePlayerCompany(player).asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    showOrders(company.get(), player);
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                })
        ;
    }
}
