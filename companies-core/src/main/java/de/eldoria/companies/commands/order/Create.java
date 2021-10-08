package de.eldoria.companies.commands.order;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.utils.ArgumentUtils;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
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
import java.util.stream.Collectors;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audience;
    private final Configuration configuration;
    private final Economy economy;
    private final IMessageBlockerService messageBlocker;
    private final AOrderData orderData;
    private final Cache<UUID, OrderBuilder> builderCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    public Create(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("field", false)
                .addArgument("value", false)
                .withPermission(Permission.Orders.CREATE)
                .build());
        audience = BukkitAudiences.create(plugin);
        this.orderData = orderData;
        this.configuration = configuration;
        this.economy = economy;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args) throws CommandException {
        if (!builderCache.asMap().containsKey(player.getUniqueId())) {
            CommandAssertions.invalidArguments(meta(), args, Argument.input("words.name", true));
            initCreation(player, args);
            return;
        }

        messageBlocker.blockPlayer(player);

        var subArgs = args.subArguments();
        var cmd = args.asString(0);

        switch (cmd.toLowerCase(Locale.ROOT)) {
            case "name":
                name(player, subArgs);
                break;
            case "add":
                add(player, subArgs);
                break;
            case "remove":
                remove(player, subArgs);
                break;
            case "price":
                price(player, subArgs);
                break;
            case "amount":
                amount(player, subArgs);
                break;
            case "done":
                done(player);
                return;
            case "cancel":
                cancel(player);
                return;
        }
        sendBuilder(player, getPlayerBuilder(player));
    }

    private void amount(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("amount", this)
                .addArgument("words.material", true)
                .addArgument("words.amount", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);
        var material = args.asMaterial(0);
        var amount = Math.max(args.asInt(1), 0);

        if (amount == 0) {
            remove(player, args);
            return;
        }

        var builder = getPlayerBuilder(player);
        builder.changeContentAmount(material, Math.min(configuration.orderSetting().maxItems() - builder.amount(material), amount));
    }

    private void price(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("price", this)
                .addArgument("words.material", true)
                .addArgument("words.price", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var material = args.asMaterial(0);
        var price = args.asDouble(1);

        CommandAssertions.min(price, 0);
        var builder = getPlayerBuilder(player);
        builder.changeContentPrice(material, Math.max(0, price));
    }

    private void name(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("name", this)
                .addArgument("words.name", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);
        CommandAssertions.invalidLength(args.join(), 32);

        var builder = getPlayerBuilder(player);
        builder.name(args.join());
    }

    @NotNull
    private OrderBuilder getPlayerBuilder(Player player) {
        var builder = builderCache.getIfPresent(player.getUniqueId());
        Objects.requireNonNull(builder);
        return builder;
    }


    private void remove(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("name", this)
                .addArgument("words.material", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var builder = getPlayerBuilder(player);
        var parse = args.asMaterial(0);

        builder.removeContent(parse);
    }

    private void cancel(Player player) {
        messageBlocker.unblockPlayer(player).thenRun(() -> player.sendMessage("words.aborted"));
        builderCache.invalidate(player.getUniqueId());
    }

    private void done(Player player) throws CommandException {
        var order = builderCache.getIfPresent(player.getUniqueId());

        messageBlocker.unblockPlayer(player);

        CommandAssertions.isTrue(order != null, "order.create.error.notActive");
        CommandAssertions.isFalse(order.elements().isEmpty(), "order.create.error.empty");

        var price = order.price();

        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= Permission.Orders.getOrderOverride(player).orElse(configuration.userSettings().maxOrders())) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "order.create.error.limitReached");
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
                                messageBlocker.unblockPlayer(player).whenComplete((unused, err) -> {
                                    messageSender().sendLocalizedMessage(player, "order.create.created");
                                    builderCache.invalidate(player.getUniqueId());
                                });
                            });
                        } else {
                            var fallbackCurr = economy.currencyNameSingular().isBlank() ? MessageComposer.escape("words.money") : economy.currencyNameSingular();
                            var curr = economy.currencyNamePlural().isBlank() ? fallbackCurr : economy.currencyNamePlural();
                            messageSender().sendLocalizedError(player, "error.insufficientCurrency",
                                    Replacement.create("currency", curr),
                                    Replacement.create("amount", economy.format(price)));
                        }
                    });
                });
    }

    private void initCreation(Player player, @NotNull Arguments args) {
        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= configuration.userSettings().maxOrders()) {
                        messageSender().sendLocalizedError(player, "order.create.error.limitReached");
                        return;
                    }
                    var name = String.join(" ", args.asArray());
                    var builder = new OrderBuilder(player.getUniqueId(), name);
                    builderCache.put(player.getUniqueId(), builder);
                    messageBlocker.blockPlayer(player);
                    sendBuilder(player, builder);
                });
    }

    private void add(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("name", this)
                .addArgument("words.material", true)
                .addArgument("words.amount", true)
                .addArgument("words.price", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var builder = getPlayerBuilder(player);
        var material = args.asMaterial(0);
        var amount = args.asInt(1);
        var price = args.asDouble(2);

        amount = Math.max(amount, 1);
        price = Math.max(price, 0);

        if (builder.materialsAmount() >= configuration.orderSetting().maxMaterials()) {
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "order.create.error.materialLimit");
            return;
        }
        if (builder.amount() >= configuration.orderSetting().maxItems()) {
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "order.create.error.itemLimit");
            return;
        }


        builder.addContent(new ItemStack(material), Math.min(amount, configuration.orderSetting().maxItems() - Math.max(1, builder.amount())),
                Math.max(0, price));
        sendBuilder(player, builder);
    }

    private void sendBuilder(Player player, OrderBuilder order) {
        var builder = MessageComposer.create().text(order.asComponent(configuration.orderSetting(), economy, orderData));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);
        audience.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 0) {
            return List.of("add", "remove", "cancel", "done");
        }
        var cmd = args[0];
        var builder = builderCache.getIfPresent(getPlayerFromSender(sender).getUniqueId());

        if (builder == null) return TabCompleteUtil.completeFreeInput(arguments.join(), 32, localizer().localize("words.name"), localizer());

        if (args.length == 1) {
            return TabCompleteUtil.complete(cmd, "name", "add", "remove", "cancel", "done");
        }

        if ("name".equalsIgnoreCase(cmd) || "create".equalsIgnoreCase(cmd)) {
            return TabCompleteUtil.completeFreeInput(ArgumentUtils.getRangeAsString(args, 1), 32, localizer().localize("words.name"), localizer());
        }

        if ("add".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                if (args[1].isEmpty()) return Collections.singletonList(localizer().localize("words.material"));
                return TabCompleteUtil.completeMaterial(args[1], true);
            }
            var price = orderData.getMaterialPrice(args[1]);
            if (args.length == 3) {
                if (args[2].isEmpty()) return Collections.singletonList(localizer().localize("words.amount"));
                var max = configuration.orderSetting().maxItems() - builder.amount();
                return TabCompleteUtil.completeInt(args[2], 1, max, localizer());
            }
            var amount = Parser.parseInt(args[2]);
            if (args.length == 4) {
                if (args[3].isEmpty()) return Collections.singletonList(localizer().localize("words.price"));
                var result = TabCompleteUtil.completeMinDouble(args[3], 0, localizer());
                result.add("Avg: " + amount.map(a -> a * price.avgPrice()).orElse(0.0));
                result.add("Min: " + amount.map(a -> a * price.minPrice()).orElse(0.0));
                result.add("Max: " + amount.map(a -> a * price.maxPrice()).orElse(0.0));
                return result;
            }
            return Collections.emptyList();
        }

        if ("price".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                if (args[1].isEmpty()) return builder.elements().stream().map(OrderContent::materialString).collect(Collectors.toList());
                return TabCompleteUtil.complete(args[1], builder.elements().stream().map(OrderContent::materialString));
            }
            if (args.length == 3) {
                return TabCompleteUtil.completeMinDouble(args[2], 0.0, localizer());
            }
            return Collections.emptyList();
        }

        if ("amount".equalsIgnoreCase(cmd)) {
            if (args.length == 2) {
                if (args[1].isEmpty()) return builder.elements().stream().map(OrderContent::materialString).collect(Collectors.toList());
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
