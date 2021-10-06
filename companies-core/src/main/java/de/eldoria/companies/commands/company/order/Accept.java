package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Accept extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Configuration configuration;
    private final IMessageBlockerService messageBlocker;

    public Accept(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("accept")
                .addArgument("words.id", true)
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
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    var companyMember = profile.member(player).get();
                    if (!companyMember.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.permission.acceptOrder");
                        return;
                    }

                    var count = orderData.retrieveCompanyOrderCount(profile).join();
                    if (count >= configuration.companySettings().level(profile.level()).orElse(CompanyLevel.DEFAULT).settings().maxOrders()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.orderLimit");
                        return;
                    }

                    var optOrder = orderData.retrieveOrderById(id).join();
                    if (optOrder.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.unkownOrder");
                    }

                    var simpleOrder = optOrder.get();
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.orderNotClaimable");
                        return;
                    }

                    if (orderData.submitOrderClaim(profile, simpleOrder).join()) {
                        messageBlocker.unblockPlayer(player).thenRun(() -> {
                            player.getServer().getPluginManager().callEvent(new OrderAcceptEvent(simpleOrder, profile));
                        });
                        return;
                    }
                    messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.couldNotClaim");
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return TabCompleteUtil.completeMinInt(args.asString(0), 0, localizer());
    }
}
