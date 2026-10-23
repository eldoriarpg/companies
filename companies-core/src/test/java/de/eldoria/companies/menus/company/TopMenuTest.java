/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopMenuTest {
    private static final LocalDateTime FOUNDED = LocalDateTime.of(2026, 1, 2, 3, 4);
    private static final String HOSTILE = "<red>Acme";

    private ServerMock server;
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock viewer;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        viewer = server.addPlayer("Viewer");
        actions = new MenuActions(plugin, viewer, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
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

    private TopMenu menu() {
        return new TopMenu(messages, economy(), new NoNavigation());
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        viewer, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    private static CompanyRank rank(int place, String tag) {
        return new CompanyRank(place, place, HOSTILE, FOUNDED, place, place, place, place, 2, tag, "gold");
    }

    private static TopView.OwnCompany own(OptionalInt rank) {
        return new TopView.OwnCompany(9, "Own", Optional.of("OWN"), Optional.of("#aabbcc"), rank);
    }

    @Test
    void theRankingSwitchesOrderingsRangesAndPagesThroughCallbacks() {
        List<CompanyRank> ranks = new ArrayList<>();
        for (var i = 1; i <= 3; i++) ranks.add(rank(i, null));
        var view = TopView.of(TopOrder.EARNED, TimeRange.LAST_7_DAYS, 2, ranks, 3, Optional.empty());

        var text = menu().chat(viewer, view, actions);

        assertFalse(text.build().contains("run_command"));
        assertFalse(text.build().contains(HOSTILE));
        assertTrue(text.build().contains("<act_ordering_level>"));
        assertTrue(text.build().contains("<act_ordering_items_delivered>"));
        assertTrue(text.build().contains("<act_range_all>"));
        assertTrue(text.build().contains("<act_range_30d>"));
        assertTrue(text.build().contains("<act_range_7d>"));
        assertTrue(text.build().contains("<act_rank_2>"));
        assertTrue(text.build().contains("<act_previous>"));
        assertTrue(text.build().contains("<act_next>"));
        assertTrue(plain(text).contains("3 | " + HOSTILE));
    }

    @Test
    void theRangeInUseIsHighlighted() {
        var view = TopView.of(TopOrder.LEVEL, TimeRange.LAST_30_DAYS, 1, List.of(), 15, Optional.empty());

        var text = menu().chat(viewer, view, actions).build();

        assertTrue(text.contains("<active>[<i18n:enums.timeRange.last_30_days>]</active>"));
        assertTrue(text.contains("<inactive>[<i18n:enums.timeRange.all>]</inactive>"));
        assertTrue(text.contains("<active>[<i18n:enums.topOrder.level>]</active>"));
    }

    @Test
    void rankedCompaniesShowTheirTagUnparsed() {
        var view = TopView.of(TopOrder.ORDERS, TimeRange.ALL, 1, List.of(rank(1, "<b>X")), 15, Optional.empty());

        var shown = plain(menu().chat(viewer, view, actions));

        assertTrue(shown.contains("1 | [<b>X] " + HOSTILE));
    }

    @Test
    void theOwnCompanyIsShownWithItsPlace() {
        var view = TopView.of(TopOrder.ORDERS, TimeRange.ALL, 1, List.of(), 15, Optional.of(own(OptionalInt.of(42))));

        var text = menu().chat(viewer, view, actions);

        assertTrue(text.build().contains("<i18n:company.top.yourCompany>"));
        assertTrue(text.build().contains("<i18n:company.top.ownRank>"));
        assertFalse(text.build().contains("company.top.notRanked"));
        assertTrue(plain(text).contains("[OWN] Own"));
    }

    @Test
    void anOwnCompanyWithoutActivityInTheRangeIsNotRanked() {
        var view = TopView.of(
                TopOrder.EARNED, TimeRange.LAST_7_DAYS, 1, List.of(), 15, Optional.of(own(OptionalInt.empty())));

        var text = menu().chat(viewer, view, actions).build();

        assertTrue(text.contains("<i18n:company.top.notRanked>"));
        assertFalse(text.contains("company.top.ownRank"));
    }

    @Test
    void aViewerWithoutCompanyGetsNoOwnLine() {
        var view = TopView.of(TopOrder.ORDERS, TimeRange.ALL, 1, List.of(), 15, Optional.empty());

        var text = menu().chat(viewer, view, actions).build();

        assertFalse(text.contains("company.top.yourCompany"));
        assertFalse(text.contains("<act_previous>"));
        assertFalse(text.contains("<act_next>"));
    }

    @Test
    void theOwnCompanyTakesItsPlaceFromTheRank() {
        var company =
                new SimpleCompany(9, "Own", FOUNDED, 1, new CompanyDetails(null, "OWN", "gold", JoinMode.INVITE_ONLY));

        var ranked = TopView.OwnCompany.of(company, Optional.of(rank(5, null)));
        var unranked = TopView.OwnCompany.of(company, Optional.empty());

        assertEquals(OptionalInt.of(5), ranked.rank());
        assertEquals(Optional.of("OWN"), ranked.tag());
        assertEquals(Optional.of("gold"), ranked.color());
        assertEquals(OptionalInt.empty(), unranked.rank());
    }

    private static final class NoNavigation implements TopMenu.Navigation {
        @Override
        public void show(Player viewer, int page, TopOrder ordering, TimeRange range) {}

        @Override
        public void profile(Player viewer, int company) {}
    }
}
