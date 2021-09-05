package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.companies.services.messages.IMessageBlockerService;
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

import java.util.Collections;
import java.util.List;

public class Self extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final IMessageBlockerService messageBlocker;

    public Self(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("self").build());
        this.companyData = companyData;
        this.orderData = orderData;
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optProfile, err) -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(player, "You are not part of a company.");
                        return;
                    }

                    var orders = orderData.retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED).join();
                    sendProfile(player, optProfile.get(), orders);
                });

    }

    private void sendProfile(Player player, CompanyProfile profile, List<SimpleOrder> orders) {
        messageBlocker.blockPlayer(player);
        var level = configuration.companySettings().level(profile.level());
        var optNextLevel = configuration.companySettings().level(profile.level() + 1);
        var composer = MessageComposer.create()
                .text(profile.name()).newLine()
                .localeCode("Level").text(": <hover:show_text:%s>%s - %s</hover>", level.map(CompanyLevel::asComponent).orElse("Unkown Level"),
                        level.map(CompanyLevel::level).orElse(-1), level.map(CompanyLevel::levelName).orElse("Unkown Level"));
        if (optNextLevel.isPresent()) {
            var nextLevel = optNextLevel.get();
            composer.text("<u><hover:show_text:%s>", nextLevel.asComponent()).localeCode("next level").text("<u></hover>");
        }
        composer.newLine()
                .localeCode("Founded").text(": %s", profile.foundedString()).newLine()
                .localeCode("Leader").text(": %s", profile.owner().player().getName()).newLine()
                .localeCode("Member").text(": %s <click:run_command:/company member>[", profile.members().size()).localeCode("list").text("]</click>").newLine()
                .localeCode("Orders").text(": %s <click:run_command:/company order list>[", orders.size()).localeCode("list").text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        composer.fillLines();
        messageBlocker.announce(player, "[x]");
        audiences.player(player).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
