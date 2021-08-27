package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Self extends EldoCommand {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audiences;
    private final Configuration configuration;

    public Self(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration) {
        super(plugin);
        this.companyData = companyData;
        this.orderData = orderData;
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);
        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendError(sender, "You are not part of a company.");
                        return;
                    }

                    orderData.retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .whenComplete(orders -> {
                                sendProfile(player, optProfile, orders);
                            });
                });
        return true;
    }

    private void sendProfile(Player player, Optional<CompanyProfile> optProfile, List<SimpleOrder> orders) {
        var profile = optProfile.get();
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

        audiences.player(player).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return  Collections.emptyList();
    }
}
