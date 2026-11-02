/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.job;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static de.eldoria.companies.menus.order.OrderFixtures.plain;
import static de.eldoria.companies.menus.order.OrderFixtures.render;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The job menus in chat: a list with an info button per job, and a job with its lines and actions, where actions that
 * change the job for good or need a text are suggested rather than run.
 */
class JobMenusTest {
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions buttons;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        player = server.addPlayer();
        buttons = new MenuActions(plugin, player, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    @Test
    void aJobShowsItsLinesAndTakesActionsByTheirKind() {
        var menu = new JobMenu(messages, () -> null, ConfirmFlow.create(plugin, (clicked, command) -> {}));
        var model = new JobMenu.Model(
                MenuText.create().text("Pit"),
                MenuText.create().text("#5 Pit"),
                List.of(
                        MenuText.create().text("Area: 64 blocks"),
                        MenuText.create().text("Price: 50")),
                List.of(
                        new JobMenu.Action("words.info", "show", "order job stock 5", JobMenu.Kind.RUN),
                        new JobMenu.Action("words.cancel", "remove", "order job cancel 5", JobMenu.Kind.CHECK),
                        JobMenu.Action.text("words.deny", "remove", "order job reject 5", "jobs.menu.reason", 256)));

        var text = menu.chat(player, model, buttons);
        var shown = plain(render(messages, player, text));

        assertTrue(shown.contains("Pit"), shown);
        assertTrue(shown.contains("Area: 64 blocks"), shown);
        assertTrue(shown.contains("Price: 50"), shown);
        assertFalse(text.build().contains("run_command"), text.build());
        assertTrue(text.build().contains("<act_0>"), text.build());
        var clicks = clicks(render(messages, player, text));
        assertTrue(clicks.contains("/order job cancel 5"), clicks.toString());
        assertTrue(clicks.contains("/order job reject 5 "), clicks.toString());
    }

    @Test
    void aListOffersEachJobAndSaysWhenThereIsNone() {
        var menu = new JobListMenu(messages);
        var full = new JobListMenu.Model(
                MenuText.create().text("Jobs"),
                MenuText.create().text("No jobs"),
                List.of(
                        new JobListMenu.Entry(
                                MenuText.create().text("#1 Pit"),
                                MenuText.create().text("#1 Pit · open"),
                                "order job info 1"),
                        new JobListMenu.Entry(
                                MenuText.create().text("#2 Wall"),
                                MenuText.create().text("#2 Wall · open"),
                                "order job info 2")));
        var empty = new JobListMenu.Model(
                MenuText.create().text("Jobs"), MenuText.create().text("No jobs"), List.of());

        var listed = menu.chat(player, full, buttons);
        var none = plain(render(messages, player, menu.chat(player, empty, buttons)));

        var shown = plain(render(messages, player, listed));
        assertTrue(shown.contains("#1 Pit") && shown.contains("#2 Wall"), shown);
        assertTrue(listed.build().contains("<act_info_1>"), listed.build());
        assertFalse(shown.contains("No jobs"), shown);
        assertTrue(none.contains("No jobs"), none);
    }

    private static List<String> clicks(Component component) {
        List<String> found = new ArrayList<>();
        var click = component.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) found.add(text.value());
        for (var child : component.children()) found.addAll(clicks(child));
        return found;
    }
}
