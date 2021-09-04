package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public Page(Plugin plugin, Search search, Economy economy) {
        super(plugin, CommandMeta.builder("page")
                .addArgument("page", true)
                .build());
        this.search = search;
        audiences = BukkitAudiences.create(plugin);
        this.economy = economy;
    }

    public void renderPage(Player player, int page) {
        var fullOrders = search.results().get(player.getUniqueId());

        var builder = Component.text().append(Component.text("Results: " + fullOrders.size())).append(Component.newline());
        var pageList = page(fullOrders, page);
        List<Component> components = new ArrayList<>();
        for (var order : pageList) {
            components.add(order.companyShortInfo(localizer(), economy));
        }

        builder.append(Component.join(Component.newline(), components)).append(Component.newline());
        if (page != 0) {
            builder.append(Component.text(Texts.LEFT_ARROW + " ").clickEvent(ClickEvent.runCommand("/company order search page " + (page - 1))));
        } else {
            builder.append(Component.text(Texts.LEFT_ARROW + " ", NamedTextColor.DARK_GRAY));
        }

        builder.append(Component.text(page + 1).append(Component.text("/"))).append(Component.text(fullOrders.size() / PAGE_SIZE + 1));

        if (fullOrders.size() - (page + 1) * PAGE_SIZE > 0) {
            builder.append(Component.text(" " + Texts.RIGHT_ARROW).clickEvent(ClickEvent.runCommand("/company order search page " + (page + 1))));
        } else {
            builder.append(Component.text(" " + Texts.RIGHT_ARROW, NamedTextColor.DARK_GRAY));
        }
        audiences.sender(player).sendMessage(builder.build());
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
