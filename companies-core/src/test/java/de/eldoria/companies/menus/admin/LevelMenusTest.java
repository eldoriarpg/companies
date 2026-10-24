/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelMenusTest {
    private static final String HOSTILE = "<red>Guild";

    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock admin;
    private MenuActions actions;
    private CompanySettings settings;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        admin = server.addPlayer("Admin");
        actions = new MenuActions(plugin, admin, MenuActions.DEFAULT_LIFETIME);
        settings = new CompanySettings();
        settings.level().get(0).levelName(HOSTILE);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    @Test
    void theListUsesCallbacksInsteadOfCommands() {
        var text = new LevelListMenu(messages, new NoActions()).chat(admin, LevelListView.of(settings), actions);

        assertNoRunCommand(text);
        assertTrue(text.build().contains("<act_create>"));
        for (var level : settings.level()) {
            assertTrue(text.build().contains("<level_" + level.level() + "_info>"));
            assertTrue(text.build().contains("<level_" + level.level() + "_remove>"));
        }
        assertTrue(plain(text).contains("1 - " + HOSTILE));
        assertTrue(suggestions(text).contains("/companyadmin level move 1 "));
    }

    @Test
    void theListShowsWhyAnActionWasRefused() {
        var view = LevelListView.of(settings).rejected(CommandFailure.of("error.secondarynode"));

        var text = new LevelListMenu(messages, new NoActions()).chat(admin, view, actions);

        assertTrue(text.build().contains("<rejection>"));
    }

    @Test
    void theEditorSuggestsTheTypedEditCommandForEveryField() {
        var level = settings.level().get(0);

        var text = new LevelMenu(messages, new NoActions())
                .chat(admin, LevelView.of(level, settings.level().size()), actions);

        assertNoRunCommand(text);
        assertTrue(text.build().contains("<act_remove>"));
        assertTrue(text.build().contains("<act_back>"));
        var suggested = suggestions(text);
        for (var field : LevelField.values()) {
            assertTrue(suggested.contains("/companyadmin level edit 1 " + field.key() + " "), field.key());
        }
        assertTrue(suggested.contains("/companyadmin level move 1 "));
        var shown = plain(text);
        assertTrue(shown.contains(HOSTILE));
        assertTrue(shown.contains("companyadmin.level.globalTax") || shown.contains("Global tax"));
    }

    @Test
    void theEditorNamesTheRejectedField() {
        var level = settings.level().get(0);
        var view = LevelView.of(level, settings.level().size());
        var invalid = (LevelForm.Invalid) LevelForm.parse(Map.of(LevelField.MAX_ORDERS, "lots"));
        var rejected = view.rejected(
                Map.of(LevelField.MAX_ORDERS, "lots"),
                new LevelView.Rejection(Optional.of(invalid.field()), invalid.failure()));

        var text = new LevelMenu(messages, new NoActions()).chat(admin, rejected, actions);

        assertTrue(text.build().contains("<rejection>"));
    }

    @Test
    void theDescriptionKeepsNamesLiteralAndLeavesOutUnsetSettings() {
        var level = settings.level().get(0);

        var description = LevelDescription.of(level);

        assertTrue(plain(description).contains(HOSTILE));
        assertFalse(description.build().contains("<max_order_size>"));
        assertFalse(description.build().contains("<tax_percent>"));
        level.settings().taxPercent(5.0);
        assertTrue(plain(LevelDescription.of(level)).contains("5 %"));
    }

    @Test
    void thePlainDescriptionEscapesTheName() {
        var level = settings.level().get(0);

        var text = level.asComponent();

        assertFalse(text.contains("<level_name>"));
        assertTrue(text.contains("\\<red>Guild"));
        assertEquals(
                plain(LevelDescription.of(level)),
                PlainTextComponentSerializer.plainText().serialize(messages.serializeMessage(admin, text)));
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText().serialize(render(text));
    }

    private Component render(MenuText text) {
        return messages.serializeMessage(
                admin, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    private void assertNoRunCommand(MenuText text) {
        assertFalse(text.build().contains("run_command"));
        assertFalse(text.build().contains(HOSTILE));
        assertTrue(clicks(render(text)).stream().noneMatch(click -> click.action() == ClickEvent.Action.RUN_COMMAND));
    }

    private List<String> suggestions(MenuText text) {
        return clicks(render(text)).stream()
                .filter(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND)
                .map(click -> ((ClickEvent.Payload.Text) click.payload()).value())
                .toList();
    }

    private static List<ClickEvent> clicks(Component component) {
        List<ClickEvent> clicks = new ArrayList<>();
        var click = component.clickEvent();
        if (click != null) clicks.add(click);
        for (var child : component.children()) clicks.addAll(clicks(child));
        return clicks;
    }

    private static final class NoActions implements LevelActions {
        @Override
        public void list(Player viewer) {}

        @Override
        public void info(Player viewer, CompanyLevel level) {}

        @Override
        public void create(Player viewer, int position) {}

        @Override
        public void save(Player viewer, LevelView view, Map<LevelField, String> entered, OptionalInt position) {}

        @Override
        public void remove(Player viewer, CompanyLevel level) {}
    }
}
