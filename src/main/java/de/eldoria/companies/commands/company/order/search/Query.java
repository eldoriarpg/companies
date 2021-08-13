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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

        if (argumentsInvalid(sender, args, 1, "<field> [value]")) return true;

        var cmd = args[0].toLowerCase(Locale.ROOT);

        var newArgs = new String[0];
        if (args.length != 1) {
            newArgs = Arrays.copyOfRange(args, 1, args.length);
        }

        switch (cmd) {
            case "name":
                name(player, newArgs);
                break;
            case "material_add":
                if (argumentsInvalid(sender, args, 1, "material_add <material>")) return true;
                materialAdd(player, newArgs);
                break;
            case "material_remove":
                materialRemove(player, newArgs);
                break;
            case "min_price":
                if (argumentsInvalid(sender, args, 1, "min_price <num>")) return true;
                if (minPrice(player, newArgs)) return true;
            case "max_price":
                if (argumentsInvalid(sender, args, 1, "max_price <num>")) return true;
                if (maxPrice(player, newArgs)) return true;
            case "min_size":
                if (argumentsInvalid(sender, args, 1, "min_size <num>")) return true;
                if (minSize(player, newArgs)) return true;
            case "max_size":
                if (argumentsInvalid(sender, args, 1, "max_size <num>")) return true;
                if (maxSize(player, newArgs)) return true;
            case "sorting":
                if (argumentsInvalid(sender, args, 1, "sorting <sorting>")) return true;
                if (sorting(player, newArgs)) return true;
            case "order":
                if (argumentsInvalid(sender, args, 1, "order <asc|desc>")) return true;
                if (order(player, newArgs)) return true;
            case "execute":
                execute(player);
                return true;
            case "clear":
                clear(player);
                break;
            default:
                // TODO unkown field
                return true;
        }
        renderSearch(player);
        return true;
    }

    private boolean order(Player player, String[] args) {
        var optional = Parser.parseBoolean(args[0], "asc", "desc");
        if (optional.isEmpty()) {
            messageSender().sendError(player, "Invalid ordering");
            return true;
        }
        getPlayerSearch(player).asc(optional.get());
        return false;
    }

    private boolean sorting(Player player, String[] args) {
        var parse = EnumUtil.parse(args[0], Sorting.class);
        if (parse == null) {
            messageSender().sendError(player, "Invalid sorting type");
            return true;
        }
        getPlayerSearch(player).sorting(parse);
        return false;
    }

    private boolean minPrice(Player player, String[] args) {
        var price = Parser.parseDouble(args[0]);
        if (price.isEmpty()) {
            messageSender().sendError(player, "invalidNumer");
            return true;
        }
        getPlayerSearch(player).minPrice(price.getAsDouble());
        return false;
    }

    private boolean maxPrice(Player player, String[] args) {
        var price = Parser.parseDouble(args[0]);
        if (price.isEmpty()) {
            messageSender().sendError(player, "invalidNumer");
            return true;
        }
        getPlayerSearch(player).maxPrice(price.getAsDouble());
        return false;
    }

    private boolean minSize(Player player, String[] args) {
        var size = Parser.parseInt(args[0]);
        if (size.isEmpty()) {
            messageSender().sendError(player, "invalidNumer");
            return true;
        }
        getPlayerSearch(player).minOrderSize(size.getAsInt());
        return false;
    }

    private boolean maxSize(Player player, String[] args) {
        var size = Parser.parseInt(args[0]);
        if (size.isEmpty()) {
            messageSender().sendError(player, "invalidNumer");
            return true;
        }
        getPlayerSearch(player).maxOrderSize(size.getAsInt());
        return false;
    }

    private void materialRemove(Player player, String[] args) {
        var search = getPlayerSearch(player);
        if (args.length == 0) {
            search.materials().clear();
            return;
        }
        search.materials().remove(String.join("_", args));
    }

    private void materialAdd(Player player, String[] args) {
        getPlayerSearch(player).materials().add(String.join("_", args));
    }

    private void name(Player player, String[] args) {
        var playerSearch = getPlayerSearch(player);

        if (args.length == 0) {
            playerSearch.name(null);
        } else {
            playerSearch.name(String.join(" ", args));
        }
    }

    private void clear(Player player) {
        searches.put(player.getUniqueId(), new SearchQuery());
        renderSearch(player);
    }

    private void renderSearch(Player player) {
        var playerSearch = getPlayerSearch(player);
        audiences.player(player).sendMessage(playerSearch.asComponent());
    }

    private void execute(Player player) {
        orderData.retrieveOrdersByQuery(getPlayerSearch(player), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .whenComplete(fullOrders -> {
                    search.results().put(player.getUniqueId(), fullOrders);
                    search.page().renderPage(player, 0);
                });
    }

    private SearchQuery getPlayerSearch(Player player) {
        return searches.computeIfAbsent(player.getUniqueId(), key -> new SearchQuery());
    }
}
