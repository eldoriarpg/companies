/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideBookTest {
    @Test
    void writesThePagesFromMiniMessage() {
        var guide =
                new GuideBook("<gold>Demo", "Companies", List.of("<bold>Start</bold>\nfound a company", "two"), true);

        var pages = guide.pageTexts();

        var plain = PlainTextComponentSerializer.plainText();
        assertEquals(
                List.of("Start\nfound a company", "two"),
                pages.stream().map(plain::serialize).toList());
    }

    @Test
    void readsTheConfigWithDefaults() {
        var config = new MemoryConfiguration();
        config.set("pages", List.of("one"));
        config.set("onFirstJoin", true);

        var guide = GuideBook.of(config);

        assertEquals("Companies demo", guide.title());
        assertEquals(List.of("one"), guide.pages());
        assertTrue(guide.onFirstJoin());
        assertFalse(GuideBook.of(new MemoryConfiguration()).onFirstJoin());
    }
}
