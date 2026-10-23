/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordPayloadsTest {
    private static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");
    private final DiscordEmbeds embeds = new DiscordEmbeds(key -> "[" + key + "]", InstantSource.fixed(NOW));

    @Test
    void orderEmbedShowsNameLinesAndPrice() {
        var embed = embeds.orderCreated(
                "Iron for the base",
                List.of(new DiscordEmbeds.Line(64, "Iron Ingot"), new DiscordEmbeds.Line(3, "Oak Log")),
                "$120.00");

        assertEquals("[discord.orderCreated]", embed.title());
        assertEquals("**Iron for the base**", embed.description());
        assertEquals("[words.items]", embed.fields().get(0).name());
        assertEquals("64× Iron Ingot\n3× Oak Log", embed.fields().get(0).value());
        assertEquals("$120.00", embed.fields().get(1).value());
        assertEquals(NOW, embed.timestamp());
    }

    @Test
    void completedOrderWithoutContentsShowsOnlyName() {
        var embed = embeds.orderCompleted("Wood", List.of(), Optional.empty());

        assertEquals("[discord.orderCompleted]", embed.title());
        assertEquals(List.of(), embed.fields());
    }

    @Test
    void levelUpShowsCompanyAndLevel() {
        var embed = embeds.levelUp("Acme_Corp", "Gold *Tier*");

        assertEquals("**Acme\\_Corp**", embed.description());
        assertEquals("Gold \\*Tier\\*", embed.fields().getFirst().value());
    }

    @Test
    void playerTextIsEscaped() {
        assertEquals(
                "\\*\\*bold\\*\\* \\<@123\\> \\[x\\]\\(https\\://x\\)",
                DiscordText.escape("**bold** <@123> [x](https://x)"));
        assertEquals("one two", DiscordText.escape("one\ntwo"));
        assertEquals("a\\\\b", DiscordText.escape("a\\b"));
    }

    @Test
    void textIsCutWithoutBreakingEscapes() {
        assertEquals("abc", DiscordText.cut("abc", 3));
        assertEquals("ab…", DiscordText.cut("abcd", 3));
        assertEquals("a…", DiscordText.cut("a\\*b", 3));
    }

    @Test
    void embedKeepsToDiscordLimits() {
        var fields = IntStream.range(0, 30)
                .mapToObj(i -> new Embed.Field("n".repeat(300), "v".repeat(2000), false))
                .toList();
        var embed = new Embed("t".repeat(300), "d".repeat(5000), 0, fields, NOW);

        assertEquals(256, embed.title().length());
        assertEquals(4096, embed.description().length());
        assertEquals(25, embed.fields().size());
        assertEquals(256, embed.fields().getFirst().name().length());
        assertEquals(1024, embed.fields().getFirst().value().length());
    }

    @Test
    void manyLinesKeepWholeRowsAndCountTheRest() {
        var lines = IntStream.range(0, 200)
                .mapToObj(i -> new DiscordEmbeds.Line(i, "Polished Blackstone Brick Stairs"))
                .toList();

        var items = DiscordEmbeds.items(lines);

        assertTrue(items.length() <= 1024, String.valueOf(items.length()));
        assertTrue(items.matches("(?s).*\\n… \\+\\d+"), items);
        assertTrue(items.lines().limit(items.lines().count() - 1).allMatch(line -> line.endsWith("Stairs")));
    }

    @Test
    void itemNamesArePlainEnglish() {
        assertEquals("Iron Ingot", DiscordText.itemName("iron_ingot"));
        assertEquals("Oak Log", DiscordText.itemName("minecraft:OAK_LOG"));
        assertEquals("Tnt", DiscordText.itemName("tnt"));
    }

    @Test
    void payloadDisablesMentionsAndCarriesEmbeds() {
        var payload = new WebhookPayload(Optional.of("Companies"), Optional.of("https://example.org/a.png"));

        var json = payload.write(List.of(embeds.levelUp("Acme", "Two")));

        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"), json);
        assertTrue(json.contains("\"username\":\"Companies\""), json);
        assertTrue(json.contains("\"avatar_url\":\"https://example.org/a.png\""), json);
        assertTrue(json.contains("\"timestamp\":\"2026-05-01T10:00:00Z\""), json);
        assertTrue(json.contains("\"color\":" + DiscordEmbeds.LEVEL_UP_COLOR), json);
        assertFalse(new WebhookPayload(Optional.empty(), Optional.empty())
                .write(List.of())
                .contains("username"));
    }
}
