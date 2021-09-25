package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.companies.util.Colors;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Page extends AdvancedCommand implements IPlayerTabExecutor {
    private static final int PAGE_SIZE = 10;
    private final Search search;
    private final Economy economy;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final IMessageBlockerService messageBlocker;

    public Page(Plugin plugin, Search search, Economy economy, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("page")
                .addArgument("words.page", true)
                .build());
        this.search = search;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        this.economy = economy;
        this.messageBlocker = messageBlocker;
    }

    public void renderPage(Player player, int page) {
        messageBlocker.blockPlayer(player);
        var fullOrders = search.results().get(player.getUniqueId());

        var builder = MessageComposer.create()
                .text("<%s>", Colors.HEADING).localeCode("words.results").text(": <%s>%s", Colors.VALUE, fullOrders.size()).newLine();

        var pageList = page(fullOrders, page);
        var components = new ArrayList<String>();
        for (var order : pageList) {
            components.add(order.companyShortInfo(economy));
        }

        builder.text(components).newLine();
        if (page != 0) {
            builder.text("<click:run_command:/company order search page %s> <%s>%s </click>", page - 1, Colors.ACTIVE, Texts.LEFT_ARROW);
        } else {
            builder.text(" <%s>%s ", Colors.INACTIVE, Texts.LEFT_ARROW);
        }

        var pageString = String.format("<%s>%s/%s", page + 1, Colors.HEADING, fullOrders.size() / PAGE_SIZE + 1);
        builder.text(pageString);

        if (!page(fullOrders, page + 1).isEmpty()) {
            builder.text("<click:run_command:/company order search page %s> <%s>%s </click>", page + 1, Colors.ACTIVE, Texts.RIGHT_ARROW);
        } else {
            builder.text("<%s> %s ", Colors.INACTIVE, Texts.RIGHT_ARROW);
        }
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);
        audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
    }

    private List<FullOrder> page(List<FullOrder> orders, int page) {
        if (page < 0) return Collections.emptyList();
        var start = page * PAGE_SIZE;
        if (start >= orders.size()) return Collections.emptyList();
        return orders.subList(start, Math.min(start + PAGE_SIZE, orders.size()));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        renderPage(player, arguments.asInt(0));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
