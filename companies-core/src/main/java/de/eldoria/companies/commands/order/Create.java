/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
    private final Configuration configuration;
    private final Economy economy;
    private final MessageBlocker messageBlocker;
    private final AOrderData orderData;
    private final Cache<UUID, OrderBuilder> builderCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5L, TimeUnit.MINUTES)
            .build();

    public Create(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("field", false)
                .addArgument("value", false)
                .withPermission(Permission.Orders.CREATE)
                .build());
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
            case "name" -> name(player, subArgs);
            case "add" -> completeAdd(player, subArgs);
            case "remove" -> remove(player, subArgs);
            case "price" -> price(player, subArgs);
            case "amount" -> amount(player, subArgs);
            case "done" -> {
                done(player);
                return;
            }
            case "cancel" -> {
                cancel(player);
                return;
            }
        }
        sendBuilder(player, getPlayerBuilder(player));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments args) {
        if (args.isEmpty()) {
            return List.of("add", "remove", "cancel", "done");
        }
        var cmd = args.asString(0);
        var builder = builderCache.getIfPresent(getPlayerFromSender(sender).getUniqueId());

        if (builder == null)
            return Completion.completeFreeInput(args.join(), 32, localizer().localize("words.name"));

        if (args.sizeIs(1)) {
            return Completion.complete(cmd, "name", "add", "remove", "cancel", "done");
        }

        if ("name".equalsIgnoreCase(cmd) || "create".equalsIgnoreCase(cmd)) {
            return Completion.completeFreeInput(args.join(1), 32, localizer().localize("words.name"));
        }

        if ("add".equalsIgnoreCase(cmd)) {
            return completeAdd(args, builder);
        }

        if ("price".equalsIgnoreCase(cmd)) {
            return completePrice(args, builder);
        }

        if ("amount".equalsIgnoreCase(cmd)) {
            return completeAmount(args, builder);
        }

        if ("remove".equalsIgnoreCase(cmd)) {
            if (args.sizeIs(2)) {
                Completion.complete(args.asString(0), builder.elements()
                        .stream()
                        .map(OrderContent::materialIdentifier));
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private static List<String> completePrice(@NotNull Arguments args, OrderBuilder builder) {
        if (args.sizeIs(2)) {
            if (args.asString(1).isBlank())
                return builder.elements()
                        .stream()
                        .map(OrderContent::materialIdentifier)
                        .collect(Collectors.toList());
            return Completion.complete(args.asString(1),
                    builder.elements().stream()
                            .map(OrderContent::materialIdentifier));
        }
        if (args.sizeIs(3)) {
            return Completion.completeMinDouble(args.asString(2), 0.0);
        }
        return Collections.emptyList();
    }

    private List<String> completeAmount(@NotNull Arguments args, OrderBuilder builder) {
        if (args.sizeIs(2)) {
            if (args.asString(1)
                    .isEmpty())
                return builder.elements()
                        .stream()
                        .map(OrderContent::materialIdentifier)
                        .collect(Collectors.toList());
            return Completion.complete(args.asString(1), builder.elements()
                    .stream()
                    .map(OrderContent::materialIdentifier));
        }

        if (args.sizeIs(3)) {
            var material = EnumUtil.parse(args.asString(1), Material.class);
            var max = configuration.orderSetting()
                    .maxItems() - builder.amount(material.orElse(null));
            return Completion.completeInt(args.asString(2), 1, max);
        }

        return Collections.emptyList();
    }

    private List<String> completeAdd(@NotNull Arguments args, OrderBuilder builder) {
        if (args.sizeIs(2)) {
            if (args.asString(1)
                    .isEmpty())
                return Collections.singletonList(localizer().localize("words.material"));
            return Completion.completeMaterial(args.asString(1), true);
        }
        var price = orderData.getMaterialPrice(args.asString(1));
        if (args.sizeIs(3)) {
            if (args.asString(2)
                    .isEmpty()) return Collections.singletonList(localizer().localize("words.amount"));
            var max = configuration.orderSetting()
                    .maxItems() - builder.amount();
            return Completion.completeInt(args.asString(2), 1, max);
        }
        var amount = Parser.parseInt(args.asString(2));
        if (args.sizeIs(4)) {
            if (args.asString(3)
                    .isEmpty()) return Collections.singletonList(localizer().localize("words.price"));
            var result = Completion.completeMinDouble(args.asString(3), 0.0);
            result.add("Avg: %.02f".formatted(amount.map(a -> a * price.avgPrice())
                    .orElse(0.0)));
            result.add("Min: %.02f".formatted(amount.map(a -> a * price.minPrice())
                    .orElse(0.0)));
            result.add("Max: %.02f".formatted(amount.map(a -> a * price.maxPrice())
                    .orElse(0.0)));
            return result;
        }
        return Collections.emptyList();
    }

    private void initCreation(Player player, @NotNull Arguments args) {
        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= configuration.userSettings()
                            .maxOrders()) {
                        messageSender().sendError(player, "order.create.error.limitReached");
                        return;
                    }
                    var name = args.join();
                    var builder = new OrderBuilder(player.getUniqueId(), name);
                    builderCache.put(player.getUniqueId(), builder);
                    messageBlocker.blockPlayer(player);
                    sendBuilder(player, builder);
                });
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

    private void completeAdd(Player player, Arguments args) throws CommandException {
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
        price = Math.max(price, 0.0);

        if (builder.materialsAmount() >= configuration.orderSetting().maxMaterials()) {
            messageSender().sendErrorActionBar(player, "order.create.error.materialLimit");
            return;
        }
        if (builder.amount() >= configuration.orderSetting().maxItems()) {
            messageSender().sendErrorActionBar(player, "order.create.error.itemLimit");
            return;
        }


        builder.addContent(new ItemStack(material), Math.min(amount, configuration.orderSetting().maxItems() - Math.max(1, builder.amount())),
                Math.max(0.0, price));
        sendBuilder(player, builder);
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

    private void price(Player player, Arguments args) throws CommandException {
        var subMeta = meta().forSubCommand("price", this)
                .addArgument("words.material", true)
                .addArgument("words.price", true)
                .build();
        CommandAssertions.invalidArguments(subMeta, args);

        var material = args.asMaterial(0);
        var price = args.asDouble(1);

        CommandAssertions.min(price, 0.0);
        var builder = getPlayerBuilder(player);
        builder.changeContentPrice(material, Math.max(0.0, price));
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
        builder.changeContentAmount(material, Math.min(configuration.orderSetting()
                .maxItems() - builder.amount(material), amount));
    }

    private void done(Player player) throws CommandException {
        var order = builderCache.getIfPresent(player.getUniqueId());

        messageBlocker.unblockPlayer(player);

        CommandAssertions.isTrue(order != null, "order.create.error.notActive");
        CommandAssertions.isFalse(order.elements()
                .isEmpty(), "order.create.error.empty", TagResolver.empty());

        var price = order.price();

        orderData.retrievePlayerOrderCount(player)
                .whenComplete(count -> {
                    if (count >= Permission.Orders.getOrderOverride(player)
                            .orElse(configuration.userSettings().maxOrders())) {
                        messageSender().sendErrorActionBar(player, "order.create.error.limitReached");
                        return;
                    }
                    CompletableBukkitFuture.supplyAsync(() -> {
                                if (!economy.has(player, price)) {
                                    return false;
                                }
                                economy.withdrawPlayer(player, price);
                                return true;
                            })
                            .whenComplete(result -> {
                                if (result) {
                                    orderData.submitOrder(player, order.build())
                                            .whenComplete(v -> messageBlocker.unblockPlayer(player)
                                                    .whenComplete((unused, err) -> {
                                                        messageSender().sendMessage(player, MessageComposer.escape("order.create.created"));
                                                        builderCache.invalidate(player.getUniqueId());
                                                    }));
                                } else {
                                    var fallbackCurr = economy.currencyNameSingular()
                                            .isBlank() ? MessageComposer.escape("words.money") : economy.currencyNameSingular();
                                    var curr = economy.currencyNamePlural()
                                            .isBlank() ? fallbackCurr : economy.currencyNamePlural();
                                    messageSender().sendError(player, "error.insufficientCurrency",
                                            Replacement.create("currency", curr),
                                            Replacement.create("amount", economy.format(price)));
                                }
                            });
                });
    }

    private void cancel(Player player) {
        messageBlocker.unblockPlayer(player)
                .thenRun(() -> messageSender().sendMessage(player, "words.aborted"));
        builderCache.invalidate(player.getUniqueId());
    }

    private void sendBuilder(Player player, OrderBuilder order) {
        var builder = MessageComposer.create()
                .text(order.asComponent(configuration.orderSetting(), economy, orderData));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine()
                    .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);
        messageSender().sendMessage(player, builder);
    }

    @NotNull
    private OrderBuilder getPlayerBuilder(Player player) {
        var builder = builderCache.getIfPresent(player.getUniqueId());
        Objects.requireNonNull(builder);
        return builder;
    }
}
