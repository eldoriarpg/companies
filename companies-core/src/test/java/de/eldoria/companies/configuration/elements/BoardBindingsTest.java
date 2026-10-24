/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.boardbindings.NpcBinding;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardBindingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @Test
    void freshFileHasNoBindings() {
        var bindings = new BoardBindings();

        assertTrue(bindings.npcs().isEmpty());
        assertEquals(Optional.empty(), bindings.board("Citizens", "1"));
    }

    @Test
    void linkedNpcIsFoundByHookIgnoringCase() {
        var bindings = new BoardBindings();

        bindings.link("Citizens", "4", "iron");

        assertEquals(Optional.of("iron"), bindings.board("citizens", "4"));
        assertEquals(Optional.empty(), bindings.board("Citizens", "5"));
        assertEquals(Optional.empty(), bindings.board("FancyNpcs", "4"));
    }

    @Test
    void npcKeyIsCaseSensitive() {
        var bindings = new BoardBindings();

        bindings.link("FancyNpcs", "Trader", "");

        assertEquals(Optional.of(""), bindings.board("FancyNpcs", "Trader"));
        assertEquals(Optional.empty(), bindings.board("FancyNpcs", "trader"));
    }

    @Test
    void linkingAgainReplacesTheBoard() {
        var bindings = new BoardBindings();
        bindings.link("Citizens", "4", "iron");
        bindings.link("FancyNpcs", "Trader", "gold");

        bindings.link("citizens", "4", "stone dirt");

        assertEquals(2, bindings.npcs().size());
        assertEquals(Optional.of("stone dirt"), bindings.board("Citizens", "4"));
    }

    @Test
    void unlinkRemovesOnlyThatNpc() {
        var bindings = new BoardBindings();
        bindings.link("Citizens", "4", "iron");
        bindings.link("Citizens", "5", "gold");

        assertTrue(bindings.unlink("CITIZENS", "4"));
        assertFalse(bindings.unlink("Citizens", "4"));

        assertEquals(Optional.empty(), bindings.board("Citizens", "4"));
        assertEquals(Optional.of("gold"), bindings.board("Citizens", "5"));
    }

    @Test
    void bindingsSurviveWritingAndReading() {
        var bindings = new BoardBindings();
        bindings.link("Citizens", "4", "iron");
        bindings.link("FancyNpcs", "Trader", "");

        var read = mapper.readValue(mapper.writeValueAsString(bindings), BoardBindings.class);

        assertEquals(
                List.of("Citizens/4/iron", "FancyNpcs/Trader/"),
                read.npcs().stream().map(BoardBindingsTest::text).toList());
    }

    @Test
    void missingValuesAreEmpty() {
        var read = mapper.readValue("""
                npcs:
                  - hook: Citizens
                  - null
                """, BoardBindings.class);

        assertEquals(
                List.of("Citizens//"),
                read.npcs().stream().map(BoardBindingsTest::text).toList());
    }

    private static String text(NpcBinding binding) {
        return binding.hook() + "/" + binding.npc() + "/" + binding.board();
    }
}
