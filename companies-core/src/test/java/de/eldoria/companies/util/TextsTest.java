/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextsTest {
    private static final String HOSTILE = "<click:run_command:'/op me'>Shop</click> it's";

    @Test
    void escapedTextRendersLiterally() {
        var component = MiniMessage.miniMessage().deserialize("<gold>" + Texts.escape(HOSTILE));

        assertEquals(HOSTILE, PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void escapedTextStaysInsideHoverArgument() {
        var hover = "<gold>" + Texts.escape(HOSTILE);
        var component = MiniMessage.miniMessage()
                .deserialize("<hover:show_text:'%s'>name</hover>".formatted(Texts.argument(hover)));

        var shown = component.children().isEmpty()
                ? component
                : component.children().getFirst();
        var hoverText = shown.hoverEvent().value();
        assertEquals("name", PlainTextComponentSerializer.plainText().serialize(component));
        assertEquals(HOSTILE, PlainTextComponentSerializer.plainText().serialize((Component) hoverText));
    }

    @Test
    void progressBarFillsTheDeliveredShare() {
        assertEquals("<add><inactive>■■■■■■■■■■", Texts.progressBar(0));
        assertEquals("<add>■■■<inactive>■■■■■■■", Texts.progressBar(0.3));
        assertEquals("<add>■■■■■■■■■■<inactive>", Texts.progressBar(1));
    }

    @Test
    void progressBarClampsInvalidValues() {
        assertEquals(Texts.progressBar(0), Texts.progressBar(Double.NaN));
        assertEquals(Texts.progressBar(0), Texts.progressBar(-1));
        assertEquals(Texts.progressBar(1), Texts.progressBar(2));
    }
}
