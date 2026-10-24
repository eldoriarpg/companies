/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.services.ProfileChange;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEditMenuTest {
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock owner;
    private MenuActions actions;
    private ProfileEditMenu menu;
    private final ProfileSettings settings = new ProfileSettings();

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        owner = server.addPlayer("Owner");
        actions = new MenuActions(plugin, owner, MenuActions.DEFAULT_LIFETIME);
        menu = new ProfileEditMenu(messages, null, () -> settings, (editor, change, then) -> {});
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private ProfileEditView view(CompanyDetails details) {
        var profile = new CompanyProfile(
                7,
                "<b>Acme",
                LocalDateTime.of(2026, 1, 2, 3, 4),
                1,
                details,
                List.of(CompanyMember.of(7, owner.getUniqueId(), CompanyPermission.OWNER.mask())));
        return ProfileEditView.of(profile, settings);
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        owner, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    private static DialogResponseView response(Map<String, String> texts) {
        return (DialogResponseView) Proxy.newProxyInstance(
                DialogResponseView.class.getClassLoader(),
                new Class<?>[] {DialogResponseView.class},
                (proxy, method, args) -> method.getName().equals("getText") ? texts.get((String) args[0]) : null);
    }

    @Test
    void theChatEditorUsesCallbacksAndSuggestions() {
        var text = menu.chat(owner, view(new CompanyDetails("<red>We dig.", "ACME", "gold", JoinMode.OPEN)), actions);

        var source = text.build();
        assertFalse(source.contains("run_command"));
        assertFalse(source.contains("<red>We dig."));
        assertFalse(source.contains("<b>Acme"));
        assertTrue(source.contains("suggest_command:'/company profile edit description '"));
        assertTrue(source.contains("suggest_command:'/company profile edit tag '"));
        assertTrue(source.contains("<clear_description>"));
        assertTrue(source.contains("<clear_tag>"));
        assertTrue(source.contains("<clear_color>"));
        assertTrue(source.contains("<pick_0>"));
        assertTrue(source.contains("<pick_" + (settings.colors().size() - 1) + ">"));
        assertTrue(source.contains("<i18n:enums.joinMode.open>"));
        var shown = plain(text);
        assertTrue(shown.contains("<red>We dig."));
        assertTrue(shown.contains("[ACME]"));
        assertTrue(shown.contains("[■]"));
    }

    @Test
    void emptyFieldsHaveNothingToClear() {
        var source = menu.chat(owner, view(CompanyDetails.NONE), actions).build();

        assertFalse(source.contains("<clear_description>"));
        assertFalse(source.contains("<clear_tag>"));
        assertFalse(source.contains("<clear_color>"));
    }

    @Test
    void theFormChangesEveryAnsweredField() {
        var change = ProfileEditMenu.change(response(Map.of(
                ProfileEditMenu.DESCRIPTION, "We dig.",
                ProfileEditMenu.TAG, "acme",
                ProfileEditMenu.COLOR, "gold")));

        assertEquals(ProfileChange.all("We dig.", "acme", "gold"), change);
    }

    @Test
    void choosingNoColourRemovesIt() {
        var change = ProfileEditMenu.change(response(Map.of(ProfileEditMenu.COLOR, ProfileEditMenu.NO_COLOR)));

        assertEquals(new ProfileChange(Optional.empty(), Optional.empty(), Optional.of("")), change);
    }
}
