package de.eldoria.companies.commands.order;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Create extends EldoCommand {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audience;
    private final Configuration configuration;
    private final Economy economy;
    private final AOrderData orderData;
    private final Cache<UUID, OrderBuilder> builderCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    public Create(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        audience = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.configuration = configuration;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);

        if (!builderCache.asMap().containsKey(player.getUniqueId())) {
            if (argumentsInvalid(sender, args, 1, "<name>")) return true;
            initCreation(player, args);
            return true;
        }

        var subArgs = Arrays.copyOfRange(args, 1, args.length);

        if ("add".equalsIgnoreCase(args[0])) {
            add(player, subArgs);
            return true;
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            remove(player, subArgs);
            return true;
        }

        if ("done".equalsIgnoreCase(args[0])) {
            done(player);
            return true;
        }

        if ("cancel".equalsIgnoreCase(args[0])) {
            cancel(player);
            return true;
        }

        return true;
    }

    private void remove(Player player, String[] args) {
        if (argumentsInvalid(player, args, 1, "<material>")) {
            return;
        }
        var builder = builderCache.getIfPresent(player.getUniqueId());
        var parse = EnumUtil.parse(args[0], Material.class);

        builder.removeContent(parse);
        audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.asComponent(configuration.orderSetting(), economy, orderData))));
    }

    private void cancel(Player player) {
        builderCache.invalidate(player.getUniqueId());
        player.sendMessage("Aborted");
    }

    private void done(Player player) {
        var order = builderCache.getIfPresent(player.getUniqueId());


        if (order == null) {
            messageSender().sendLocalizedError(player, "No order builder registered.");
            return;
        }

        var price = order.price();

        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= configuration.userSettings().maxOrders()) {
                        messageSender().sendError(player, "Order limit reached.");
                        return;
                    }
                    CompletableBukkitFuture.supplyAsync(() -> {
                        if (!economy.has(player, price)) {
                            return false;
                        }
                        economy.withdrawPlayer(player, price);
                        return true;
                    }).whenComplete(result -> {
                        if (result) {
                            orderData.submitOrder(player, order.build()).whenComplete(v -> {
                                messageSender().sendLocalizedMessage(player, "Created UwU");
                                builderCache.invalidate(player.getUniqueId());
                            });
                        } else {
                            messageSender().sendLocalizedError(player, "Not enough money.");
                        }
                    });
                });
    }

    private void initCreation(Player player, String[] args) {
        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= configuration.userSettings().maxOrders()) {
                        messageSender().sendLocalizedError(player, "error.tooMuchOrders");
                        return;
                    }
                    var name = String.join(" ", args);
                    var builder = new OrderBuilder(player.getUniqueId(), name);
                    builderCache.put(player.getUniqueId(), builder);
                    audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.asComponent(configuration.orderSetting(), economy, orderData))));
                });
    }

    private void add(Player player, String[] args) {
        if (argumentsInvalid(player, args, 3, "<material> <amount> <price>")) {
            return;
        }
        var builder = builderCache.getIfPresent(player.getUniqueId());
        var parse = EnumUtil.parse(args[0], Material.class);
        var amount = Parser.parseInt(args[1]);
        var price = Parser.parseDouble(args[2]);
        if (price.isEmpty()) {
            messageSender().sendError(player, "error.invalidNumber");
            return;
        }

        if (builder.materialsAmount() >= configuration.orderSetting().maxMaterials()) {
            messageSender().sendError(player, "Material limit reached");
            return;
        }
        if (builder.amount() >= configuration.orderSetting().maxItems()) {
            messageSender().sendError(player, "Item limit reached");
            return;
        }


        builder.addContent(new ItemStack(parse), Math.min(amount.getAsInt(), configuration.orderSetting().maxItems() - builder.amount()),
                Math.max(0, price.getAsDouble()));
        audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.asComponent(configuration.orderSetting(), economy, orderData))));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        if (args.length == 0) {
            return List.of("add", "remove", "cancel", "done");
        }
        var cmd = args[0];
        var builder = builderCache.getIfPresent(getPlayerFromSender(sender).getUniqueId());

        if (builder == null) return Collections.singletonList("<name>");

        if (args.length == 1) {
            return TabCompleteUtil.complete(cmd, "add", "remove", "cancel", "done");
        }
        if ("add".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                if (args[1].isEmpty()) return Collections.singletonList("material");
                return TabCompleteUtil.completeMaterial(args[1], true);
            }
            if (args.length == 3) {
                if (args[2].isEmpty()) return Collections.singletonList("amount");
                var max = configuration.orderSetting().maxItems() - builder.amount();
                return TabCompleteUtil.completeInt(args[2], 1, max, localizer());
            }
            var materialPrice = orderData.getMaterialPrice(args[2]);
            if (args.length == 4) {
                if (args[3].isEmpty()) return Collections.singletonList("price");
                var result = TabCompleteUtil.completeDouble(args[3], 0, 20000, localizer());
                if (materialPrice.isPresent()) {
                    var price = materialPrice.get();
                    result.add("Avg: " + price.avgPrice());
                    result.add("Min: " + price.minPrice());
                    result.add("Max: " + price.maxPrice());
                }
                return result;
            }
            return Collections.emptyList();
        }
        if ("remove".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                TabCompleteUtil.complete(args[0], builder.elements().stream().map(OrderContent::materialString).collect(Collectors.toList()));
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
