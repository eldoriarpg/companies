/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Orders, items and rendering helpers for the order menu tests.
 */
public final class OrderFixtures {
    /**
     * Id of the company working on the fixture orders, which no owner view may show.
     */
    public static final int COMPANY = 4242;

    /**
     * Owner of the fixture orders, whom no company view may show.
     */
    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    /**
     * Worker who delivered to the fixture orders.
     */
    public static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-0000000b0b00");

    /**
     * When the fixture orders were claimed or last updated.
     */
    public static final LocalDateTime CLAIMED = LocalDateTime.of(2026, 1, 1, 12, 0);

    private OrderFixtures() {}

    /**
     * An economy writing amounts as {@code $1.50}.
     *
     * @return the economy
     */
    public static Economy economy() {
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(), new Class<?>[] {Economy.class}, (proxy, method, args) -> {
                    if (method.getName().equals("format")) {
                        return String.format(Locale.ROOT, "$%.2f", (double) args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * An item with a name of its own, as an exact line requests it.
     *
     * @param material material of the item
     * @return the item
     */
    public static ItemStack named(Material material) {
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

    /**
     * An order {@code 7 "<red>Wood"} with ten oak logs, three of them delivered, and three exact stones.
     *
     * @param state state of the order
     * @return the order
     */
    public static FullOrder order(OrderState state) {
        var logs = new OrderContent(0, new ItemStack(Material.OAK_LOG), false, 10, 15);
        logs.deliveries(List.of(new DeliveredStack(1, WORKER, new ItemStack(Material.OAK_LOG), 3)));
        var stones = new OrderContent(1, named(Material.STONE), true, 3, 6);
        return new FullOrder(7, OWNER, "<red>Wood", CLAIMED, COMPANY, CLAIMED, state, 2.5, 1, 0, List.of(logs, stones));
    }

    /**
     * @param messages renders the text
     * @param player   viewing player
     * @param text     text to render
     * @return the rendered text
     */
    public static Component render(MessageSender messages, Player player, MenuText text) {
        return messages.serializeMessage(
                player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    /**
     * @param component rendered text
     * @return its plain text
     */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Whether a component or one of its children shows an item when hovered.
     *
     * @param component rendered text
     * @return true when an item is shown
     */
    public static boolean showsItem(Component component) {
        var hover = component.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_ITEM) return true;
        return component.children().stream().anyMatch(OrderFixtures::showsItem);
    }

    /**
     * @param type record type
     * @return lower-case names of its components
     */
    public static List<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }
}
