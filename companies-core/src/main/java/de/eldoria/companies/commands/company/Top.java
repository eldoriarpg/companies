package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.util.Colors;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Top extends AdvancedCommand implements IPlayerTabExecutor {
    private static final int PAGE_SIZE = 15;
    private static final TopOrder DEFAULT_ORDER = TopOrder.ORDERS;
    private final MiniMessage miniMessage;
    private final BukkitAudiences audiences;
    private final ACompanyData companyData;
    private final MessageBlocker messageBlocker;

    public Top(Plugin plugin, ACompanyData companyData, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("top")
                .addArgument("words.page", false)
                .addArgument("words.ordering", false)
                .build());
        this.companyData = companyData;
        this.messageBlocker = messageBlocker;
        miniMessage = MiniMessage.miniMessage();
        audiences = BukkitAudiences.create(plugin);
    }

    private void renderPage(Player player, int page, TopOrder orders) {
        companyData.retrieveRanking(orders, page, PAGE_SIZE)
                .whenComplete(companyRanks -> sendPage(player, page, orders, companyRanks));
    }

    private void sendPage(Player player, int page, TopOrder order, List<CompanyRank> ranks) {
        messageBlocker.blockPlayer(player);
        var composer = MessageComposer.create().text("<%s>", Colors.HEADING).localeCode("company.top.ranking").newLine()
                .text("<%s>", Colors.NAME).localeCode("Order: ");
        for (var value : TopOrder.values()) {
            composer.text("<click:run_command:/company top %s %s><%s>[", page, value.name(), Colors.active(order == value)).localeCode(value.name()).text("]</click>");
        }
        composer.newLine();
        for (var rank : ranks) {
            composer.text("<%s>%s | <%s><hover:show_text:'%s'><gold>%s</hover>", Colors.NAME, rank.rank(), Colors.VALUE, rank.asComponent(), rank.name()).newLine();
        }
        if (page > 1) {
            composer.text("<click:run_command:/company top %s %s><%s>%s</click>", page - 1, order.name(), Colors.ACTIVE, Texts.LEFT_ARROW);
        } else {
            composer.text("<%s>%s", Colors.INACTIVE, Texts.LEFT_ARROW);
        }
        composer.localeCode("words.page").text(" <aqua>%s ", page);

        if (ranks.size() < PAGE_SIZE) {
            composer.text("<%s>%s", Colors.INACTIVE, Texts.RIGHT_ARROW);
        } else {
            composer.text("<click:run_command:/company top %s %s><%s>%s</click>", page + 1, order.name(), Colors.ACTIVE, Texts.RIGHT_ARROW);
        }
        if (messageBlocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        composer.prependLines(25);
        audiences.sender(player).sendMessage(miniMessage.deserialize(localizer().localize(composer.build())));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var order = args.asEnum(1, TopOrder.class, DEFAULT_ORDER);
        renderPage(player, args.asInt(0, 1), order);
    }
}
