/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.services.MembershipService.ApplicationOverview;
import de.eldoria.companies.services.MembershipService.InviteOverview;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipMenusTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);
    private static final String HOSTILE = "<red>Acme";

    private final UUID inviter = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID applicant = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
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

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        player, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    private static void assertNoCommands(MenuText text) {
        assertFalse(text.build().contains("run_command"));
        assertFalse(text.build().contains(HOSTILE));
    }

    private InviteOverview overview(List<Invite> received, List<Invite> sent, List<Application> applications) {
        return new InviteOverview(
                received, sent, applications, Map.of(inviter, "Inviter", target, "<bold>Target"), NOW);
    }

    private Invite received() {
        return new Invite(1, 7, HOSTILE, UUID.randomUUID(), inviter, NOW.minusHours(1), NOW.plusHours(5));
    }

    private Invite sent() {
        return new Invite(2, 8, "Own", target, inviter, NOW, NOW.plusDays(1).plusHours(2));
    }

    private Application ownApplication() {
        return new Application(3, 9, "Bakers", UUID.randomUUID(), "Hi", NOW, NOW.plusMinutes(30));
    }

    @Test
    void invitesShowEveryKindWithItsButtons() {
        var menu = new InvitesMenu(messages, new NoInviteActions());
        var text =
                menu.chat(player, overview(List.of(received()), List.of(sent()), List.of(ownApplication())), actions);

        assertNoCommands(text);
        var built = text.build();
        assertTrue(built.contains("<act_accept_0>"));
        assertTrue(built.contains("<act_deny_0>"));
        assertTrue(built.contains("<act_revoke_0>"));
        assertTrue(built.contains("<act_withdraw_0>"));
        var shown = plain(text);
        assertTrue(shown.contains(HOSTILE));
        assertTrue(shown.contains("Inviter"));
        assertTrue(shown.contains("<bold>Target"));
        assertTrue(shown.contains("5h 0m"));
        assertTrue(shown.contains("1d 2h"));
        assertTrue(shown.contains("30m"));
    }

    @Test
    void sectionsWithoutEntriesAreLeftOutButReceivedIsAlwaysShown() {
        var menu = new InvitesMenu(messages, new NoInviteActions());
        var text = menu.chat(player, overview(List.of(), List.of(), List.of()), actions);

        var built = text.build();
        assertTrue(built.contains("company.invites.received"));
        assertTrue(built.contains("company.invites.none"));
        assertFalse(built.contains("company.invites.sent"));
        assertFalse(built.contains("company.invites.applications"));
    }

    @Test
    void theDialogBodyHasNoChatButtons() {
        var menu = new InvitesMenu(messages, new NoInviteActions());
        var body = menu.entries(overview(List.of(received()), List.of(sent()), List.of()), Optional.empty());

        assertFalse(body.build().contains("act_"));
    }

    @Test
    void applicationsShowTheMessageAsTyped() {
        var hostileMessage = "<click:run_command:/op me>Hire me";
        var applications = new ArrayList<Application>();
        for (var i = 0; i < ApplicationsMenu.PAGE_SIZE + 2; i++) {
            applications.add(
                    new Application(i, 7, "Miners", applicant, hostileMessage, NOW.minusHours(3), NOW.plusHours(21)));
        }
        var overview =
                new ApplicationOverview(Optional.empty(), HOSTILE, applications, Map.of(applicant, "Applicant"), NOW);
        var menu = new ApplicationsMenu(messages, null, new NoApplicationActions());

        var text = menu.chat(player, ApplicationsMenu.Model.of(overview), actions);

        assertNoCommands(text);
        var built = text.build();
        assertFalse(built.contains("/op me"));
        assertTrue(built.contains("<act_accept_4>"));
        assertFalse(built.contains("<act_accept_5>"));
        assertTrue(built.contains("<act_next>"));
        var shown = plain(text);
        assertTrue(shown.contains(hostileMessage));
        assertTrue(shown.contains("3h 0m"));
        assertTrue(shown.contains("21h 0m"));
        assertEquals(1, ApplicationsMenu.Model.of(overview).page(1).page().number());
        assertEquals(
                2, ApplicationsMenu.Model.of(overview).page(1).page().items().size());
    }

    @Test
    void noApplicationsSaySo() {
        var overview = new ApplicationOverview(Optional.empty(), "Miners", List.of(), Map.of(), NOW);
        var menu = new ApplicationsMenu(messages, null, new NoApplicationActions());

        var built =
                menu.chat(player, ApplicationsMenu.Model.of(overview), actions).build();

        assertTrue(built.contains("company.applications.none"));
        assertFalse(built.contains("act_"));
    }

    @Test
    void timeSpansUseTheirTwoLargestUnits() {
        assertEquals("0m", TimeSpans.between(NOW, NOW.minusHours(1)));
        assertEquals("59m", TimeSpans.between(NOW, NOW.plusMinutes(59).plusSeconds(59)));
        assertEquals("1h 1m", TimeSpans.between(NOW, NOW.plusMinutes(61)));
        assertEquals(
                "2d 3h", TimeSpans.between(NOW, NOW.plusDays(2).plusHours(3).plusMinutes(4)));
    }

    private static final class NoInviteActions implements InvitesMenu.Handlers {
        @Override
        public void accept(Player player, Invite invite) {}

        @Override
        public void deny(Player player, Invite invite) {}

        @Override
        public void revoke(Player player, Invite invite) {}

        @Override
        public void withdraw(Player player, Application application) {}
    }

    private static final class NoApplicationActions implements ApplicationsMenu.Handlers {
        @Override
        public void accept(Player player, Application application) {}

        @Override
        public void deny(Player player, Application application) {}
    }
}
