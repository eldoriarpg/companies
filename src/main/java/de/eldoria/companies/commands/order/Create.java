package de.eldoria.companies.commands.order;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Create extends EldoCommand {
    Cache<UUID, OrderBuilder> builderCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();
    private final BukkitAudiences audience;
    private final Configuration configuration;
    private final Economy economy;
    private final OrderData orderData;

    public Create(Plugin plugin, OrderData orderData, Economy economy, Configuration configuration) {
        super(plugin);
        audience = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.configuration = configuration;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) {
            return true;
        }
        var player = getPlayerFromSender(sender);

        if (!builderCache.asMap().containsKey(player.getUniqueId())) {
            initCreation(player, args);
            return true;
        }

        var subArgs = Arrays.copyOfRange(args, 1, args.length);

        if ("add".equalsIgnoreCase(subArgs[0])) {
            add(player, args);
            return true;
        }

        if ("done".equalsIgnoreCase(subArgs[0])) {
            done(player, args);
            return true;
        }

        if ("cancel".equalsIgnoreCase(subArgs[0])) {
            cancel(player);
            return true;
        }

        return true;
    }

    private void cancel(Player player) {
        builderCache.invalidate(player.getUniqueId());
        player.sendMessage("Aborted");
    }

    private void done(Player player, String[] args) {
        var order = builderCache.getIfPresent(player.getUniqueId());

        var price = order.price();

        if (order == null) {
            messageSender().sendLocalizedError(player, "No order builder registered.");
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
                });
            } else {
                messageSender().sendLocalizedError(player, "Not enough money.");
            }
        });
        return;
    }

    private void initCreation(Player player, String[] args) {
        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count.get() >= configuration.userSettings().maxOrders()) {
                        messageSender().sendLocalizedError(player, "error.tooMuchOrders");
                        return;
                    }
                    var name = String.join(" ", args);
                    var builder = new OrderBuilder(player.getUniqueId(), name);
                    builderCache.put(player.getUniqueId(), builder);
                    var component = builder.asComponent(configuration.orderSetting(), localizer(), economy);
                    audience.player(player).sendMessage(component);
                });
    }

    private void add(Player player, String[] args) {
        if (argumentsInvalid(player, args, 3, "material amount price")) {
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

        builder.addContent(new ItemStack(parse), amount.getAsInt(), (float) price.getAsDouble());
    }
}
