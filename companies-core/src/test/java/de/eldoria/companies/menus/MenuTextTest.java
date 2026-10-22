/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuTextTest {
    private Plugin plugin;
    private MessageSender sender;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sender = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText().serialize(render(text));
    }

    private Component render(MenuText text) {
        return sender.serializeMessage(text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    private static List<NamedTextColor> colors(Component component) {
        var colors = new ArrayList<NamedTextColor>();
        if (component.color() instanceof NamedTextColor named) colors.add(named);
        component.children().forEach(child -> colors.addAll(colors(child)));
        return colors;
    }

    @Test
    void namedPlaceholdersAreResolved() {
        var text = MenuText.create()
                .text("<order_name>: <amount> for <price>", Values.text("order_name", "Wood"))
                .text("", Values.number("amount", 64), Values.number("price", 2.5));

        assertEquals("Wood: 64 for 2.50", plain(text));
    }

    @Test
    void textValuesAreNotParsed() {
        var text = MenuText.create().text("<order_name>", Values.text("order_name", "<red>x</red>"));

        assertEquals("<red>x</red>", plain(text));
        assertFalse(colors(render(text)).contains(NamedTextColor.RED));
    }

    @Test
    void styleTagsKeepWorkingNextToPlaceholders() {
        var text = MenuText.create().text("<name><order_name>", Values.text("order_name", "Wood"));

        assertEquals("Wood", plain(text));
        assertTrue(colors(render(text)).contains(NamedTextColor.AQUA));
    }

    @Test
    void localeTextsBecomeI18nTags() {
        var text = MenuText.create().locale("words.price").space().text("x").newLine();

        assertEquals("<i18n:words.price> x\n", text.build());
    }

    @Test
    void aKeyIsUsedOnlyOnce() {
        var text = MenuText.create().text("<amount>", Values.number("amount", 1));

        assertThrows(IllegalArgumentException.class, () -> text.text("<amount>", Values.number("amount", 2)));
        assertThrows(IllegalArgumentException.class, () -> MenuText.create()
                .text("<amount>", Values.number("amount", 1), Values.number("amount", 2)));
    }

    @Test
    void appendMergesPlaceholders() {
        var text = MenuText.create()
                .text("<first> ", Values.text("first", "a"))
                .append(MenuText.create().text("<second>", Values.text("second", "b")));

        assertEquals("a b", plain(text));
        assertEquals(2, text.replacements().size());
    }

    @Test
    void appendRejectsClashingKeys() {
        var text = MenuText.create().text("<amount>", Values.number("amount", 1));
        var other = MenuText.create().text("<amount>", Values.number("amount", 2));

        assertThrows(IllegalArgumentException.class, () -> text.append(other));
    }

    @Test
    void styleTagNamesAreReserved() {
        for (var tag : new StyleSettings().tags().keySet()) {
            assertThrows(IllegalArgumentException.class, () -> Values.text(tag, "x"), tag);
        }
        assertThrows(IllegalArgumentException.class, () -> Values.text("prefix", "x"));
        assertThrows(IllegalArgumentException.class, () -> Values.text("default", "x"));
        assertThrows(IllegalArgumentException.class, () -> Values.text("i18n", "x"));
    }

    @Test
    void standardTagNamesAreReserved() {
        assertThrows(IllegalArgumentException.class, () -> Values.text("red", "x"));
        assertThrows(IllegalArgumentException.class, () -> Values.click("click", ClickEvent.runCommand("/x")));
        assertThrows(IllegalArgumentException.class, () -> Values.component("key", Component.text("x")));
    }

    @Test
    void keysAreSnakeCase() {
        assertThrows(IllegalArgumentException.class, () -> Values.text("orderName", "x"));
        assertThrows(IllegalArgumentException.class, () -> Values.text("order-name", "x"));
        assertThrows(IllegalArgumentException.class, () -> Values.text("_order", "x"));
        assertThrows(IllegalArgumentException.class, () -> Values.text("", "x"));
        assertEquals("order_name", Keys.require("order_name"));
    }

    @Test
    void indexedKeysAreJoined() {
        assertEquals("line_2_item", Keys.of("line", 2, "item"));
        assertThrows(IllegalArgumentException.class, () -> Keys.of("name"));
    }

    @Test
    void numbersAreFormatted() {
        assertEquals("1000", plainOf(Values.number("n", 1000L)));
        assertEquals("0.33", plainOf(Values.number("n", 1.0 / 3)));
        assertEquals("12.50", plainOf(Values.number("n", new BigDecimal("12.5"))));
    }

    @Test
    void clickPlaceholdersStyleTheEnclosedText() {
        var text = MenuText.create()
                .text("<act_done>[Done]</act_done>", Values.click("act_done", ClickEvent.runCommand("/done")));

        var rendered = MiniMessage.miniMessage()
                .deserialize(text.build(), text.replacements().toArray(TagResolver[]::new));
        assertEquals(ClickEvent.runCommand("/done"), clickOf(rendered));
        assertEquals("[Done]", PlainTextComponentSerializer.plainText().serialize(rendered));
    }

    private static ClickEvent clickOf(Component component) {
        if (component.clickEvent() != null) return component.clickEvent();
        return component.children().stream()
                .map(MenuTextTest::clickOf)
                .filter(event -> event != null)
                .findFirst()
                .orElse(null);
    }

    private static String plainOf(TagResolver.Single value) {
        var rendered = MiniMessage.miniMessage().deserialize("<" + value.key() + ">", value);
        return PlainTextComponentSerializer.plainText().serialize(rendered);
    }
}
