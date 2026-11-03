/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.PerkDefinition;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.perks.PerkService;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list of {@code /company perks}: every perk with its state, and a buy button on the perks the company may buy,
 * only for a member who may buy them.
 */
class PerksMenuTest {
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        player = server.addPlayer("Viewer");
        actions = new MenuActions(plugin, player, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    @Test
    void onlyAPerkTheCompanyMayBuyGetsABuyButton() {
        var model = new PerksMenu.Model(
                List.of(
                        offer("builder-tool", PerkService.State.AVAILABLE),
                        offer("fast-delivery", PerkService.State.OWNED),
                        offer("second-home", PerkService.State.LOCKED)),
                true);

        var lines = plain(menu().chat(player, model, actions)).split("\n");

        assertEquals(4, lines.length);
        assertTrue(lines[1].startsWith("[company.perks.state.available]"), lines[1]);
        assertTrue(lines[1].endsWith("[company.perks.buy]"), lines[1]);
        assertFalse(lines[2].contains("company.perks.buy"), lines[2]);
        assertFalse(lines[3].contains("company.perks.buy"), lines[3]);
    }

    @Test
    void aMemberWhoMayNotBuySeesNoButton() {
        var model = new PerksMenu.Model(List.of(offer("builder-tool", PerkService.State.AVAILABLE)), false);

        assertFalse(plain(menu().chat(player, model, actions)).contains("company.perks.buy"));
    }

    private PerksMenu menu() {
        return new PerksMenu(messages, price -> "$" + price, (clicker, offer) -> {});
    }

    private static PerkService.Offer offer(String id, PerkService.State state) {
        return new PerkService.Offer(
                new PerkDefinition(id, id, "what it does", 100, 2), new BigDecimal("100.00"), 2, state, null);
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        player, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }
}
