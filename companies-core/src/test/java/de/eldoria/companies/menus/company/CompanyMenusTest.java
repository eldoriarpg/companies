/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyMenusTest {
    private static final LocalDateTime FOUNDED = LocalDateTime.of(2026, 1, 2, 3, 4);
    private static final String HOSTILE = "<red>Acme";

    private ServerMock server;
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock owner;
    private PlayerMock member;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        owner = server.addPlayer("Owner");
        member = server.addPlayer("Zed");
        actions = new MenuActions(plugin, owner, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private CompanyProfile profile() {
        return new CompanyProfile(
                7,
                HOSTILE,
                FOUNDED,
                1,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(7, owner.getUniqueId(), CompanyPermission.OWNER.mask()),
                        CompanyMember.of(7, member.getUniqueId(), CompanyPermission.INVITE.mask())));
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

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        owner,
                        text.build(),
                        text.replacements()
                                .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new)));
    }

    private static void assertNoCommands(MenuText text) {
        assertFalse(text.build().contains("run_command"));
        assertFalse(text.build().contains(HOSTILE));
    }

    @Test
    void theOwnProfileLinksItsListsThroughCallbacks() {
        var stats = new CompanyStats(7, HOSTILE, FOUNDED, 2, 5, 30_000, 500);
        var view = ProfileView.own(profile(), new CompanySettings(), stats, 3, false);
        var menu = new ProfileMenu(messages, economy(), new NoNavigation());

        var text = menu.chat(owner, view, actions);

        assertNoCommands(text);
        assertTrue(text.build().contains("<act_members>"));
        assertTrue(text.build().contains("<act_orders>"));
        var shown = plain(text);
        assertTrue(shown.startsWith(HOSTILE));
        assertTrue(shown.contains("$30000.00/$20000.00"));
        assertTrue(shown.contains("5/10"));
    }

    @Test
    void theExternalProfileHasNoOrderList() {
        var view = ProfileView.external(profile(), new CompanySettings());

        var text = new ProfileMenu(messages, economy(), new NoNavigation()).chat(owner, view, actions);

        assertNoCommands(text);
        assertTrue(text.build().contains("<act_members>"));
        assertFalse(text.build().contains("<act_orders>"));
    }

    @Test
    void theProfileShowsTagNameAndDescriptionUnparsed() {
        var details = new CompanyDetails("<red>We dig.", "ACME", "gold", JoinMode.APPLICATIONS);
        var view = ProfileView.external(profile().withDetails(details), new CompanySettings());

        var text = new ProfileMenu(messages, economy(), new NoNavigation()).chat(owner, view, actions);

        assertNoCommands(text);
        assertFalse(text.build().contains("<red>We dig."));
        assertTrue(text.build().contains("<i18n:enums.joinMode.applications>"));
        var shown = plain(text);
        assertTrue(shown.startsWith("[ACME] " + HOSTILE));
        assertTrue(shown.contains("<red>We dig."));
    }

    @Test
    void onlyEditorsGetTheEditButton() {
        var menu = new ProfileMenu(messages, economy(), new NoNavigation());

        var editable = menu.chat(owner, ProfileView.own(profile(), new CompanySettings(), null, 0, true), actions);
        var readOnly = menu.chat(owner, ProfileView.own(profile(), new CompanySettings(), null, 0, false), actions);

        assertTrue(editable.build().contains("<act_edit>"));
        assertFalse(readOnly.build().contains("<act_edit>"));
    }

    @Test
    void managersGetAPermissionButtonForEveryMemberButTheOwner() {
        var view = MemberView.of(profile(), owner);

        var text = new MemberMenu(messages, economy(), null, (viewer, name) -> {})
                .chat(owner, MemberMenu.Model.of(view), actions);

        assertNoCommands(text);
        assertTrue(text.build().contains("<act_member_0>"));
        assertTrue(text.build().contains("<act_member_1>"));
        assertFalse(text.build().contains("<act_permissions_0>"));
        assertTrue(text.build().contains("<act_permissions_1>"));
    }

    @Test
    void theDetailsShowStatusAndPermissionsToMembers() {
        var view = MemberView.of(profile(), member);
        var model = MemberMenu.Model.of(view).select(member.getUniqueId());

        var text = new MemberMenu(messages, economy(), null, (viewer, name) -> {}).chat(owner, model, actions);

        assertNoCommands(text);
        assertTrue(text.build().contains("<act_back>"));
        assertFalse(text.build().contains("<act_permissions>"));
        var shown = plain(text);
        assertTrue(shown.startsWith("Zed"));
        assertTrue(shown.contains("invite"));
    }

    private static final class NoNavigation implements ProfileMenu.Navigation {
        @Override
        public void members(Player viewer, ProfileView profile) {}

        @Override
        public void orders(Player viewer) {}

        @Override
        public void edit(Player viewer) {}
    }
}
