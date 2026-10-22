/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StyledMessageSenderTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
                                                  .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                                                  .build();
    private StyleSettings style = new StyleSettings();
    private Plugin plugin;
    private StyledMessageSender sender;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sender = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, () -> style);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static TextColor colorOf(Component component) {
        if (component.color() != null) return component.color();
        return component.children().stream().map(StyledMessageSenderTest::colorOf).filter(c -> c != null).findFirst().orElse(null);
    }

    @Test
    void senderIsTheRegisteredPluginSender() {
        assertSame(sender, MessageSender.getPluginMessageSender(plugin));
    }

    @Test
    void tagsUseTheDefaultStyle() {
        assertEquals(NamedTextColor.GOLD, colorOf(sender.serializeMessage("<heading>text")));
        assertEquals(NamedTextColor.DARK_AQUA, colorOf(sender.serializeMessage("text")));
        assertEquals(TextColor.fromHexString("#f05316"), colorOf(sender.serializeError("text")));
    }

    @Test
    void changedStyleAppliesWithoutReplacingTheSender() {
        style = mapper.readValue("""
                prefix: "[Shop]"
                heading: "#123456"
                message: white
                error: dark_red
                """, StyleSettings.class);

        assertEquals(TextColor.color(0x123456), colorOf(sender.serializeMessage("<heading>text")));
        assertEquals(NamedTextColor.WHITE, colorOf(sender.serializeMessage("text")));
        assertEquals(NamedTextColor.DARK_RED, colorOf(sender.serializeError("text")));
        assertEquals("[Shop]", PlainTextComponentSerializer.plainText().serialize(sender.prefix()));
    }
}
