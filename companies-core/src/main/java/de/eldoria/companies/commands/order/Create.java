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
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.util.Menus;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Threading;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Builds an order in a chat menu and charges the player when it is done.
 * <p>
 * A draft is kept for 15 minutes after the last click. Clicks on the builder after that answer that the draft expired
 * instead of starting a new order named after the click.
 */
public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    private static final Set<String> BUILDER_ACTIONS =
            Set.of("name", "add", "remove", "price", "amount", "done", "cancel");
    private final Configuration configuration;
    private final Economy economy;
    private final MessageBlocker messageBlocker;
    private final AOrderData orderData;
    private final OrderLimits orderLimits;
    private final Cache<UUID, OrderBuilder> builderCache =
            CacheBuilder.newBuilder().expireAfterAccess(15L, TimeUnit.MINUTES).build();

    public Create(
            Plugin plugin,
            AOrderData orderData,
            OrderLimits orderLimits,
            Economy economy,
            Configuration configuration,
            MessageBlocker messageBlocker) {
        super(
                plugin,
                CommandMeta.builder("create")
                        .addArgument("field", false)
                        .addArgument("value", false)
                        .withPermission(Permission.Orders.CREATE)
                        .build());
        this.orderData = orderData;
        this.orderLimits = orderLimits;
        this.configuration = configuration;
        this.economy = economy;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args)
            throws CommandException {
        if (!builderCache.asMap().containsKey(player.getUniqueId())) {
            if (!args.isEmpty() && BUILDER_ACTIONS.contains(args.asString(0).toLowerCase(Locale.ROOT))) {
                draftExpired(player);
                return;
            }
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
    public @Nullable List<String> onTabComplete(
            @NotNull Player sender, @NotNull String alias, @NotNull Arguments args) {
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
                return Completion.complete(
                        args.asString(1), builder.elements().stream().map(OrderContent::materialIdentifier));
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private static List<String> completePrice(@NotNull Arguments args, OrderBuilder builder) {
        if (args.sizeIs(2)) {
            if (args.asString(1).isBlank())
                return builder.elements().stream()
                        .map(OrderContent::materialIdentifier)
                        .collect(Collectors.toList());
            return Completion.complete(
                    args.asString(1), builder.elements().stream().map(OrderContent::materialIdentifier));
        }
        if (args.sizeIs(3)) {
            return Completion.completeMinDouble(args.asString(2), 0.0);
        }
        return Collections.emptyList();
    }

    private List<String> completeAmount(@NotNull Arguments args, OrderBuilder builder) {
        if (args.sizeIs(2)) {
            if (args.asString(1).isEmpty())
                return builder.elements().stream()
                        .map(OrderContent::materialIdentifier)
                        .collect(Collectors.toList());
            return Completion.complete(
                    args.asString(1), builder.elements().stream().map(OrderContent::materialIdentifier));
        }

        if (args.sizeIs(3)) {
            var material = EnumUtil.parse(args.asString(1), Material.class);
            var max = configuration.orderSetting().maxItems() - builder.amount(material.orElse(null));
            return Completion.completeInt(args.asString(2), 1, max);
        }

        return Collections.emptyList();
    }

    private List<String> completeAdd(@NotNull Arguments args, OrderBuilder builder) {
        if (args.sizeIs(2)) {
            if (args.asString(1).isEmpty())
                return Collections.singletonList(localizer().localize("words.material"));
            return Completion.completeMaterial(args.asString(1), true);
        }
        var price = orderData.getMaterialPrice(args.asString(1));
        if (args.sizeIs(3)) {
            if (args.asString(2).isEmpty())
                return Collections.singletonList(localizer().localize("words.amount"));
            var max = configuration.orderSetting().maxItems() - builder.amount();
            return Completion.completeInt(args.asString(2), 1, max);
        }
        var amount = Parser.parseInt(args.asString(2));
        if (args.sizeIs(4)) {
            if (args.asString(3).isEmpty())
                return Collections.singletonList(localizer().localize("words.price"));
            var result = Completion.completeMinDouble(args.asString(3), 0.0);
            result.add(
                    "Avg: %.02f".formatted(amount.map(a -> a * price.avgPrice()).orElse(0.0)));
            result.add(
                    "Min: %.02f".formatted(amount.map(a -> a * price.minPrice()).orElse(0.0)));
            result.add(
                    "Max: %.02f".formatted(amount.map(a -> a * price.maxPrice()).orElse(0.0)));
            return result;
        }
        return Collections.emptyList();
    }

    private void initCreation(Player player, @NotNull Arguments args) {
        orderLimits.usage(player).whenComplete(usage -> {
            if (usage.reached()) {
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
        if (!Double.isFinite(price)) CommandAssertions.invalidNumber();

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

        builder.addContent(
                new ItemStack(material),
                Math.min(amount, configuration.orderSetting().maxItems() - builder.amount()),
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
        if (!Double.isFinite(price)) CommandAssertions.invalidNumber();

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
        builder.changeContentAmount(
                material, Math.min(configuration.orderSetting().maxItems() - builder.amount(material), amount));
    }

    /**
     * Charges the player the price and the fee and saves the draft as an order.
     * <p>
     * The draft leaves the cache before anything is charged, so only the first of several clicks on done goes on. A draft
     * which could not be turned into an order is put back, so the player can fix it.
     *
     * @param player player owning the draft
     * @throws CommandException when the draft is empty
     */
    private void done(Player player) throws CommandException {
        var draft = builderCache.getIfPresent(player.getUniqueId());

        messageBlocker.unblockPlayer(player);

        CommandAssertions.isTrue(draft != null, "order.create.error.notActive");
        CommandAssertions.isFalse(draft.elements().isEmpty(), "order.create.error.empty", TagResolver.empty());

        var order = builderCache.asMap().remove(player.getUniqueId());
        if (order == null) return;

        var price = order.price();
        var fee = configuration.orderSetting().fee().of(price);
        var total = price + fee;

        orderLimits.usage(player).whenComplete(usage -> {
            if (usage.reached()) {
                restoreDraft(player, order);
                messageSender().sendErrorActionBar(player, "order.create.error.limitReached");
                return;
            }
            if (!economy.has(player, total)
                    || !economy.withdrawPlayer(player, total).transactionSuccess()) {
                var fallbackCurr = economy.currencyNameSingular().isBlank()
                        ? MessageComposer.escape("words.money")
                        : economy.currencyNameSingular();
                var curr = economy.currencyNamePlural().isBlank() ? fallbackCurr : economy.currencyNamePlural();
                restoreDraft(player, order);
                messageSender()
                        .sendError(
                                player,
                                "error.insufficientCurrency",
                                Replacement.create("currency", curr),
                                Replacement.create("amount", economy.format(total)));
                return;
            }
            orderData
                    .submitOrder(player, order.build(fee))
                    .asFuture()
                    .exceptionally(err -> {
                        plugin().getLogger().log(Level.SEVERE, "Could not save order", err);
                        return false;
                    })
                    .thenAcceptAsync(
                            created -> {
                                if (!created) {
                                    economy.depositPlayer(player, total);
                                    restoreDraft(player, order);
                                    messageSender().sendError(player, "error.saveFailed");
                                    return;
                                }
                                messageBlocker.unblockPlayer(player).whenComplete((unused, err) -> messageSender()
                                        .sendMessage(player, MessageComposer.escape("order.create.created")));
                            },
                            Threading.mainThread(plugin()));
        });
    }

    private void restoreDraft(Player player, OrderBuilder order) {
        builderCache.asMap().putIfAbsent(player.getUniqueId(), order);
    }

    private void draftExpired(Player player) {
        messageBlocker.unblockPlayer(player).thenRun(() -> messageSender()
                .sendError(player, "order.create.draftExpired"));
    }

    private void cancel(Player player) {
        messageBlocker.unblockPlayer(player).thenRun(() -> messageSender().sendMessage(player, "words.aborted"));
        builderCache.invalidate(player.getUniqueId());
    }

    private void sendBuilder(Player player, OrderBuilder order) {
        var builder =
                MessageComposer.create().text(order.asComponent(configuration.orderSetting(), economy, orderData));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        builder.prependLines(25);
        Menus.show(messageBlocker, player, builder);
    }

    @NotNull
    private OrderBuilder getPlayerBuilder(Player player) {
        var builder = builderCache.getIfPresent(player.getUniqueId());
        Objects.requireNonNull(builder);
        return builder;
    }
}
