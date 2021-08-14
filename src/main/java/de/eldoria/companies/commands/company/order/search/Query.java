package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// TODO: Testing
public class Query extends EldoCommand {
    private final AOrderData orderData;
    private final Search search;
    private final Map<UUID, SearchQuery> searches = new HashMap<>();
    private final BukkitAudiences audiences;

    public Query(Plugin plugin, AOrderData orderData, Search search) {
        super(plugin);
        this.orderData = orderData;
        this.search = search;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);

        if (args.length == 0) {
            renderSearch(player);
            return true;
        }

        if (!super.onCommand(sender, command, label, args)) {
            return true;
        }
        renderSearch(player);
        return true;
    }

    private void renderSearch(Player player) {
        var playerSearch = getPlayerSearch(player);
        audiences.player(player).sendMessage(playerSearch.asComponent());
    }

    public SearchQuery getPlayerSearch(Player player) {
        return searches.computeIfAbsent(player.getUniqueId(), key -> new SearchQuery());
    }

    public void reset(Player player) {
        searches.put(player.getUniqueId(), new SearchQuery());
    }
}
