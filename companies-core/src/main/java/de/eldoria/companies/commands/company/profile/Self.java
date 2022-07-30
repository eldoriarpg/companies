package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Self extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Self(Plugin plugin, ACompanyData companyData, AOrderData orderData, Configuration configuration, MessageBlocker messageBlocker) {
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
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept((optProfile) -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }

                    var orders = orderData.retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED).join();
                    sendProfile(player, optProfile.get(), orders);
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void sendProfile(Player player, CompanyProfile profile, List<SimpleOrder> orders) {
        messageBlocker.blockPlayer(player);
        var level = configuration.companySettings().level(profile.level()).orElse(CompanyLevel.DEFAULT);
        var optNextLevel = configuration.companySettings().level(profile.level() + 1);
        var composer = MessageComposer.create()
                .text("<%s>", Colors.HEADING).text(profile.name()).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.level")
                .text(": <hover:show_text:'%s'><%s>%s - %s</hover>", level.asComponent(), Colors.VALUE, level.level(), level.levelName());
        if (optNextLevel.isPresent()) {
            var nextLevel = optNextLevel.get();
            composer.space().text("<u><hover:show_text:'%s'><%s>", nextLevel.asComponent(), Colors.SHOW).localeCode("company.level.nextLevel").text("</u></hover>");
        }
        composer.newLine()
                .text("<%s>", Colors.NAME).localeCode("words.founded")
                .text(": <%s>%s", Colors.VALUE, profile.foundedString()).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.leader")
                .text(": <%s>%s", Colors.VALUE, profile.owner().player().getName()).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.member")
                .text(": <%s>%s <click:run_command:/company member><%s>[", Colors.VALUE, profile.members().size(), Colors.SHOW)
                .localeCode("words.list").text("]</click>").newLine()
                .text("<%s>", Colors.NAME).localeCode("words.orders")
                .text(": <%s>%s <click:run_command:/company order list><%s>[", Colors.VALUE, orders.size(), Colors.SHOW)
                .localeCode("words.list").text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        composer.prependLines(25);
        messageBlocker.announce(player, "[x]");
        audiences.player(player).sendMessage(miniMessage.deserialize(localizer().localize(composer.build())));
    }
}
