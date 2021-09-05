package de.eldoria.companies.commands.order;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.utils.ArgumentUtils;
import de.eldoria.eldoutilities.utils.EnumUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audience;
    private final Configuration configuration;
    private final Economy economy;
    private final AOrderData orderData;
    private final Cache<UUID, OrderBuilder> builderCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    public Create(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("field", false)
                .addArgument("value", false)
                .build());
        audience = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.configuration = configuration;
        this.economy = economy;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args) throws CommandException {
        if (!builderCache.asMap().containsKey(player.getUniqueId())) {
            CommandAssertions.invalidArguments(meta(), args, Argument.input("name", true));
            initCreation(player, args);
            return;
        }

        var subArgs = args.subArguments();
        var cmd = args.asString(0);

        switch (cmd.toLowerCase(Locale.ROOT)) {
            case "name":
                name(player, subArgs);
            case "add":
                add(player, subArgs);
            case "remove":
                remove(player, subArgs);
            case "price":
                price(player, subArgs);
            case "amount":
                amount(player, subArgs);
            case "done":
                done(player);
            case "cancel":
                cancel(player);
        }
    }

    private void amount(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("amount", this)
                .addArgument("material", true)
                .addArgument("amount", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);
        var amount = args.asInt(0);
        var material = args.asMaterial(1);

        var builder = getPlayerBuilder(player);
        builder.changeContentAmount(material, Math.min(configuration.orderSetting().maxItems() - builder.amount(material), amount));
        sendBuilder(player, builder);
    }

    private void price(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("price", this)
                .addArgument("material", true)
                .addArgument("price", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var material = args.asMaterial(0);
        var price = args.asDouble(1);

        var builder = getPlayerBuilder(player);
        builder.changeContentPrice(material, Math.max(0, price));
        sendBuilder(player, builder);
    }

    private void name(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("name", this)
                .addArgument("name", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var builder = getPlayerBuilder(player);
        builder.name(String.join(" ", args.asArray()));
        sendBuilder(player, builder);
    }

    @NotNull
    private OrderBuilder getPlayerBuilder(Player player) {
        var builder = builderCache.getIfPresent(player.getUniqueId());
        Objects.requireNonNull(builder);
        return builder;
    }


    private void remove(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("name", this)
                .addArgument("material", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var builder = getPlayerBuilder(player);
        var parse = args.asMaterial(0);

        builder.removeContent(parse);
        sendBuilder(player, builder);
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

    private void initCreation(Player player, @NotNull Arguments args) {
        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= configuration.userSettings().maxOrders()) {
                        messageSender().sendLocalizedError(player, "error.tooMuchOrders");
                        return;
                    }
                    var name = String.join(" ", args.asArray());
                    var builder = new OrderBuilder(player.getUniqueId(), name);
                    builderCache.put(player.getUniqueId(), builder);
                    sendBuilder(player, builder);
                });
    }

    private void add(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("name", this)
                .addArgument("material", true)
                .addArgument("amount", true)
                .addArgument("price", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var builder = getPlayerBuilder(player);
        var material = args.asMaterial(0);
        var amount = args.asInt(1);
        var price = args.asDouble(2);

        if (builder.materialsAmount() >= configuration.orderSetting().maxMaterials()) {
            messageSender().sendError(player, "Material limit reached");
            return;
        }
        if (builder.amount() >= configuration.orderSetting().maxItems()) {
            messageSender().sendError(player, "Item limit reached");
            return;
        }


        builder.addContent(new ItemStack(material), Math.min(amount, configuration.orderSetting().maxItems() - Math.max(1, builder.amount())),
                Math.max(0, price));
        sendBuilder(player, builder);
    }

    private void sendBuilder(Player player, OrderBuilder builder) {
        audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.asComponent(configuration.orderSetting(), economy, orderData))));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 0) {
            return List.of("add", "remove", "cancel", "done");
        }
        var cmd = args[0];
        var builder = builderCache.getIfPresent(getPlayerFromSender(sender).getUniqueId());

        if (builder == null) return Collections.singletonList("<name>");

        if (args.length == 1) {
            return TabCompleteUtil.complete(cmd, "name", "add", "remove", "cancel", "done");
        }

        if ("name".equalsIgnoreCase(cmd) || "create".equalsIgnoreCase(cmd)) {
            return TabCompleteUtil.completeFreeInput(ArgumentUtils.getRangeAsString(args, 1), 32, "<name>", localizer());
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

        if ("price".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                if (args[1].isEmpty()) return Collections.singletonList("material");
                return TabCompleteUtil.complete(args[1], builder.elements().stream().map(OrderContent::materialString));
            }
            if (args.length == 3) {
                return TabCompleteUtil.completeDouble(args[2], 0.0, 100000000000.0, localizer());
            }
            return Collections.emptyList();
        }

        if ("amount".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                if (args[1].isEmpty()) return Collections.singletonList("material");
                return TabCompleteUtil.complete(args[1], builder.elements().stream().map(OrderContent::materialString));
            }

            if (args.length == 3) {
                var material = EnumUtil.parse(args[1], Material.class);
                var max = configuration.orderSetting().maxItems() - builder.amount(material.orElse(null));
                return TabCompleteUtil.completeInt(args[2], 1, max, localizer());
            }

            return Collections.emptyList();
        }

        if ("remove".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                TabCompleteUtil.complete(args[0], builder.elements().stream().map(OrderContent::materialString));
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
