/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.stats.MemberStats;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberStatsSortTest {
    private ServerMock server;
    private Plugin plugin;
    private PlayerMock owner;
    private PlayerMock anna;
    private PlayerMock zed;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        owner = server.addPlayer("Owner");
        anna = server.addPlayer("Anna");
        zed = server.addPlayer("Zed");
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private CompanyProfile profile() {
        return new CompanyProfile(
                7,
                "Acme",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                1,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(7, owner.getUniqueId(), CompanyPermission.OWNER.mask()),
                        CompanyMember.of(7, anna.getUniqueId(), 0),
                        CompanyMember.of(7, zed.getUniqueId(), 0)));
    }

    private MemberContributions contributions() {
        return MemberContributions.of(
                List.of(
                        new MemberStats(owner.getUniqueId(), 5, 50.0, 1),
                        new MemberStats(anna.getUniqueId(), 30, 20.0, 4),
                        new MemberStats(zed.getUniqueId(), 10, 300.0, 2)),
                List.of(new MemberStats(owner.getUniqueId(), 5, 50.0, 1)));
    }

    private MemberView view(PlayerMock viewer) {
        return MemberView.of(profile(), viewer).withContributions(contributions());
    }

    private static List<String> names(List<MemberEntry> entries) {
        return entries.stream().map(MemberEntry::name).toList();
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

    @Test
    void byNameTheOwnerComesFirst() {
        assertEquals(List.of("Owner", "Anna", "Zed"), names(view(anna).sorted(MemberSorting.NAME, TimeRange.ALL)));
    }

    @Test
    void contributionsSortHighestFirst() {
        var view = view(anna);

        assertEquals(List.of("Anna", "Zed", "Owner"), names(view.sorted(MemberSorting.ITEMS, TimeRange.ALL)));
        assertEquals(List.of("Zed", "Owner", "Anna"), names(view.sorted(MemberSorting.EARNED, TimeRange.ALL)));
        assertEquals(List.of("Anna", "Zed", "Owner"), names(view.sorted(MemberSorting.ORDERS, TimeRange.ALL)));
    }

    @Test
    void membersWithoutContributionInTheRangeTieByName() {
        assertEquals(
                List.of("Owner", "Anna", "Zed"), names(view(anna).sorted(MemberSorting.ITEMS, TimeRange.LAST_30_DAYS)));
    }

    @Test
    void outsidersGetNoContributions() {
        var outsider = server.addPlayer("Outsider");

        var view = view(outsider);

        assertTrue(view.contributions().isEmpty());
        assertEquals(List.of("Owner", "Anna", "Zed"), names(view.sorted(MemberSorting.ITEMS, TimeRange.ALL)));
    }

    @Test
    void sortingsAreParsedByIdAndCycle() {
        assertEquals(MemberSorting.EARNED, MemberSorting.byId("Earned").orElseThrow());
        assertTrue(MemberSorting.byId("level").isEmpty());
        assertEquals(MemberSorting.NAME, MemberSorting.ORDERS.next());
    }

    @Test
    void memberListShowsContributionsOnlyToMembers() {
        var messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        var actions = new MenuActions(plugin, anna, MenuActions.DEFAULT_LIFETIME);
        var menu = new MemberMenu(messages, economy(), null, (viewer, name) -> {});
        var outsider = server.addPlayer("Outsider");

        var sorted = menu.chat(anna, MemberMenu.Model.of(view(anna), MemberSorting.EARNED, TimeRange.ALL), actions);
        var foreign = menu.chat(outsider, MemberMenu.Model.of(view(outsider)), actions);

        assertTrue(sorted.build().contains("<act_sort_items>"));
        assertTrue(sorted.build().contains("<act_range_1>"));
        assertFalse(sorted.build().contains("run_command"));
        assertTrue(plain(messages, sorted).contains("Zed $300.00"));
        assertFalse(foreign.build().contains("act_sort"));
        assertFalse(plain(messages, foreign).contains("$"));
    }

    private String plain(MessageSender messages, MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        anna, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }
}
