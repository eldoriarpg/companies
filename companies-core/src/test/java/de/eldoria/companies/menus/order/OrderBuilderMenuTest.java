/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.price.PriceBasis;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.order.OrderBuilderMenu.Screen;
import de.eldoria.companies.menus.order.OrderBuilderMenu.View;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBuilderMenuTest {
    private ServerMock server;
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private OrderBuilderMenu menu;
    private final OrderBuilder draft = new OrderBuilder(UUID.randomUUID(), "<red>Wood");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        player = server.addPlayer();
        var controller =
                new OrderBuilderController(new CommandDependencies(plugin, null, null, null, null, null, null, null));
        menu = new OrderBuilderMenu(controller, economy(), messages);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static Economy economy() {
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(), new Class<?>[] {Economy.class}, (proxy, method, args) -> {
                    if (method.getName().equals("format")) {
                        return String.format(Locale.ROOT, "$%.2f", (double) args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * An item whose name can be shown, as exact lines show the name instead of the material.
     */
    private static ItemStack named(Material material) {
        return new ItemStackMock(material) {
            @Override
            public Component effectiveName() {
                return Component.text("Named " + material.name());
            }

            @Override
            public HoverEvent<HoverEvent.ShowItem> asHoverEvent(UnaryOperator<HoverEvent.ShowItem> op) {
                return HoverEvent.showItem(op.apply(HoverEvent.ShowItem.showItem(material.getKey(), 1)));
            }
        };
    }

    private static OrderSettings settings(int maxLines, int maxItems) {
        var settings = new OrderSettings();
        settings.maxMaterials(maxLines);
        settings.maxItems(maxItems);
        return settings;
    }

    private MenuText chat(OrderSettings settings) {
        return chat(settings, Map.of("oak_log", MaterialPrice.ofOrders("oak_log", 2, 1, 3)));
    }

    private MenuText chat(OrderSettings settings, Map<String, MaterialPrice> prices) {
        var view =
                DraftView.of(draft, settings, material -> prices.getOrDefault(material, new MaterialPrice(material)));
        var actions = new MenuActions(plugin, player, Duration.ofMinutes(15));
        return menu.chat(player, new View(view, new Screen.Main(), Optional.empty()), actions);
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        player, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    @Test
    void chatUsesNoCommandPlumbing() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 10, 1.5);
        draft.add(named(Material.STONE), true, 3, 2);

        var text = chat(settings(5, 100)).build();

        assertFalse(text.contains("run_command:"), text);
        assertTrue(text.contains("suggest_command:/order draft add "), text);
        assertTrue(text.contains("suggest_command:/order draft amount 2 "), text);
        assertTrue(text.contains("suggest_command:/order draft price 1 "), text);
    }

    @Test
    void chatShowsTheDraftAsBefore() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 10, 1.5);

        var shown = plain(chat(settings(5, 100)));

        assertTrue(shown.contains("<red>Wood [words.change]"), shown);
        assertTrue(shown.contains("$1.50 × 10 = $15.00"), shown);
        assertTrue(shown.contains("words.lines: 1/5"), shown);
        assertTrue(shown.contains("words.items: 10/100"), shown);
        assertTrue(shown.contains("words.price: $15.00"), shown);
        assertTrue(shown.contains("[words.done] [words.cancel]"), shown);
    }

    @Test
    void marketAverageOnlyOnMaterialLinesWithMarketData() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 1, 1);
        draft.add(named(Material.OAK_LOG), true, 1, 1);
        draft.add(new ItemStack(Material.STONE), false, 1, 1);

        var text = chat(settings(5, 100));

        assertTrue(text.build().contains("<line_1_market>"));
        assertFalse(text.build().contains("<line_2_market>"));
        assertFalse(text.build().contains("<line_3_market>"));
    }

    @Test
    void aMaterialOnlyTheShopsKnowOffersTheirPriceAndSaysWhereItComesFrom() {
        draft.add(new ItemStack(Material.STONE), false, 1, 1);
        draft.add(new ItemStack(Material.OAK_LOG), false, 1, 1);

        var text = chat(settings(5, 100), Map.of("stone", MaterialPrice.ofShops("stone", 4, PriceBasis.LISTINGS)))
                .build();

        assertTrue(text.contains("<line_1_market>"), text);
        assertTrue(text.contains("order.draft.price.shops"), text);
        assertTrue(text.contains("order.draft.price.basis.LISTINGS"), text);
        assertFalse(text.contains("<line_2_market>"), text);
    }

    @Test
    void theHoverShowsTheOrdersAndTheShopsTheUsedOneFirst() {
        draft.add(new ItemStack(Material.STONE), false, 1, 1);
        var fewOrders = new MaterialPrice("stone", 2, 1, 3, 1, 4, PriceBasis.TRADES, 3);
        var manyOrders = new MaterialPrice("stone", 2, 1, 3, 5, 4, PriceBasis.TRADES, 3);

        var shopsFirst = chat(settings(5, 100), Map.of("stone", fewOrders)).build();
        var ordersFirst = chat(settings(5, 100), Map.of("stone", manyOrders)).build();

        assertTrue(shopsFirst.indexOf("order.draft.price.shops") < shopsFirst.indexOf("words.avg"), shopsFirst);
        assertTrue(ordersFirst.indexOf("words.avg") < ordersFirst.indexOf("order.draft.price.shops"), ordersFirst);
    }

    @Test
    void addButtonsOnlyBelowTheLimits() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 5, 1);

        assertTrue(chat(settings(5, 6)).build().contains("/order draft add "));
        assertFalse(chat(settings(5, 5)).build().contains("/order draft add "));
        assertFalse(chat(settings(1, 6)).build().contains("/order draft add "));
    }

    @Test
    void addScreenTakesWhatWasEntered() {
        var entered =
                Screen.Add.empty().entered(new Response(Map.of("material", "oak_log", "unit_price", "1,5"), 7.2f), 5);

        assertEquals(new Screen.Add(Optional.empty(), "oak_log", 5, "1,5"), entered);
        assertEquals(Material.OAK_LOG, entered.item().getType());
        assertEquals("2.25", entered.withUnitPrice(2.25).unitPrice());
    }

    @Test
    void addScreenRefusesUnknownMaterials() {
        var entered = Screen.Add.empty().entered(new Response(Map.of("material", "gold_coin"), null), 5);

        var failure = assertThrows(CommandFailure.class, entered::item);
        assertEquals("order.draft.error.unknownMaterial", failure.localeKey());
    }

    @Test
    void editScreenTakesWhatWasEntered() {
        var entered = new Screen.Edit(2, 4, "1").entered(new Response(Map.of("unit_price", "3"), 9f), 20);

        assertEquals(new Screen.Edit(2, 9, "3"), entered);
        assertEquals(new Screen.Edit(2, 9, "0.5"), entered.withUnitPrice(0.5));
    }

    private record Response(
            Map<String, String> texts, @Nullable Float amount) implements DialogResponseView {
        @Override
        public BinaryTagHolder payload() {
            return BinaryTagHolder.binaryTagHolder("{}");
        }

        @Override
        public @Nullable String getText(String key) {
            return texts.get(key);
        }

        @Override
        public @Nullable Boolean getBoolean(String key) {
            return null;
        }

        @Override
        public @Nullable Float getFloat(String key) {
            return key.equals("amount") ? amount : null;
        }
    }
}
