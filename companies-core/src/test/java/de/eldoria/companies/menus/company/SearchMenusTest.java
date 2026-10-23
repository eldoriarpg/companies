/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.company.order.search.SearchMode;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.company.order.search.SearchResults;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchMenusTest {
    private final OrderSearch search = new OrderSearch(
            new CommandDependencies(null, null, null, null, null, null, null, null), (p, id) -> {}, (p, id) -> {});
    private ServerMock server;
    private PlayerMock player;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        actions = new MenuActions(MockBukkit.createMockPlugin(), player, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theChatFormUsesCallbacksInsteadOfCommands() {
        var query = new SearchQuery();
        query.name("<red>x");
        query.materials(List.of("STONE", "DIRT"));

        var text = new SearchMenu(search, null).chat(player, SearchMenu.Model.of(query), actions);

        assertFalse(text.build().contains("run_command"));
        assertTrue(text.build().contains("<act_search>"));
        assertTrue(text.build().contains("<act_material_1_remove>"));
        assertFalse(text.build().contains("<red>x"));
    }

    @Test
    void theChatFormOffersSavingTheSearchAsAlert() {
        var text = new SearchMenu(search, null).chat(player, SearchMenu.Model.of(new SearchQuery()), actions);

        assertTrue(text.build().contains("suggest_command:'/company alerts add '"));
        assertTrue(text.build().contains("<i18n:company.order.search.saveAlert>"));
    }

    @Test
    void theSoloFormHasNoAlertsAndTypesSoloCommands() {
        var solo = new OrderSearch(
                new CommandDependencies(null, null, null, null, null, null, null, null),
                SearchMode.SOLO,
                (p, query) -> null,
                (p, id) -> {},
                (p, id) -> {});

        var text = new SearchMenu(solo, null).chat(player, SearchMenu.Model.of(new SearchQuery()), actions);

        assertFalse(text.build().contains("company alerts add"), text.build());
        assertFalse(text.build().contains("company.order.search.saveAlert"), text.build());
        assertTrue(text.build().contains("suggest_command:'/order work search query name '"), text.build());
        assertFalse(text.build().contains("/company order search"), text.build());
    }

    @Test
    void theSoloResultsNameTheSoloLimit() {
        var solo = new OrderSearch(
                new CommandDependencies(null, null, null, null, null, null, null, null),
                SearchMode.SOLO,
                (p, query) -> null,
                (p, id) -> {},
                (p, id) -> {});
        var results = new SearchResults(List.of(), 0, true, true);

        var text = new SearchResultsMenu(solo, null, null)
                .chat(player, new SearchResultsMenu.Model(results, Page.of(results.orders(), 0, 10)), actions);

        assertTrue(text.build().contains("solo.search.limitReached"), text.build());
        assertFalse(text.build().contains("<act_work_alone>"), text.build());
    }

    @Test
    void companyResultsOfferWorkingAloneWhenAllowed() {
        var results = new SearchResults(List.of(), 5, false, false);

        var offered = new SearchResultsMenu(search, null, null)
                .chat(player, new SearchResultsMenu.Model(results, Page.of(results.orders(), 0, 10), true), actions)
                .build();
        var hidden = new SearchResultsMenu(search, null, null)
                .chat(player, new SearchResultsMenu.Model(results, Page.of(results.orders(), 0, 10)), actions)
                .build();

        assertTrue(offered.contains("<act_work_alone>"), offered);
        assertFalse(offered.contains("run_command"), offered);
        assertFalse(hidden.contains("<act_work_alone>"), hidden);
    }

    @Test
    void theChatResultsUseCallbacksInsteadOfCommands() {
        var results = new SearchResults(List.of(), 5, true, true);

        var text = new SearchResultsMenu(search, null, null)
                .chat(player, new SearchResultsMenu.Model(results, Page.of(results.orders(), 0, 10)), actions);

        assertFalse(text.build().contains("run_command"));
        assertTrue(text.build().contains("<act_change>"));
        assertTrue(text.build().contains("company.order.search.limitReached"));
    }
}
