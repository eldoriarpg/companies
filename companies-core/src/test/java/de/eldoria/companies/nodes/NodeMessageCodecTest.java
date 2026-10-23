/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.ConfigSynced;
import de.eldoria.companies.nodes.NodeMessage.Heartbeat;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.nodes.NodeMessage.NodeStopped;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import de.eldoria.companies.nodes.NodeMessage.RankingChanged;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeMessageCodecTest {
    private static final UUID NODE = UUID.fromString("6f1c1b0e-3f5a-4a52-9c1e-1d2f0b9a7c41");
    private static final UUID PLAYER = UUID.fromString("0b9a7c41-1d2f-4a52-9c1e-6f1c1b0e3f5a");

    private final NodeMessageCodec codec = new NodeMessageCodec();

    @Test
    void everyMessageSurvivesTheRoundTrip() {
        List<NodeMessage> messages = List.of(
                new InboxNew(12L, PLAYER),
                new PlayerPresence(PLAYER, true),
                new CacheInvalidate(NodeCaches.COMPANY, "4"),
                new CacheInvalidate(NodeCaches.PRICES, null),
                new RankingChanged(),
                new BoardsChanged(),
                new ConfigSynced(),
                new Heartbeat(List.of(PLAYER)),
                new NodeStopped());

        for (var message : messages) {
            var decoded = codec.decode(codec.encode(NODE, message));
            assertEquals(Optional.of(new NodeMessageCodec.Envelope(NODE, message)), decoded, message.toString());
        }
    }

    @Test
    void envelopeIsFlatAndVersioned() {
        var json = JsonMapper.builder().build().readTree(codec.encode(NODE, new InboxNew(12L, PLAYER)));

        assertEquals(NodeMessageCodec.VERSION, json.get("v").asInt());
        assertEquals("inbox.new", json.get("type").asString());
        assertEquals(NODE.toString(), json.get("node").asString());
        assertEquals(12L, json.get("entry").asLong());
        assertEquals(PLAYER.toString(), json.get("player").asString());
    }

    @Test
    void unknownFieldsAreIgnored() {
        var text = """
                {"v":1,"type":"inbox.new","node":"%s","entry":3,"player":"%s","priority":"high"}""".formatted(NODE, PLAYER);

        assertEquals(Optional.of(new NodeMessageCodec.Envelope(NODE, new InboxNew(3L, PLAYER))), codec.decode(text));
    }

    @Test
    void missingKeyReadsAsEveryValue() {
        var text = """
                {"v":1,"type":"cache.invalidate","node":"%s","cache":"prices"}""".formatted(NODE);

        var message = (CacheInvalidate) codec.decode(text).orElseThrow().message();

        assertNull(message.key());
        assertTrue(message.changed().isEmpty());
    }

    @Test
    void unknownTypesOtherVersionsAndBrokenTextAreSkipped() {
        assertEquals(Optional.empty(), codec.decode("""
                {"v":1,"type":"event.order","node":"%s"}""".formatted(NODE)));
        assertEquals(Optional.empty(), codec.decode("""
                {"v":2,"type":"ranking.changed","node":"%s"}""".formatted(NODE)));
        assertEquals(Optional.empty(), codec.decode("""
                {"type":"ranking.changed","node":"%s"}""".formatted(NODE)));
        assertEquals(Optional.empty(), codec.decode("""
                {"v":1,"type":"ranking.changed","node":"not a uuid"}"""));
        assertEquals(Optional.empty(), codec.decode("""
                {"v":1,"type":"inbox.new","node":"%s","entry":"twelve"}""".formatted(NODE)));
        assertEquals(Optional.empty(), codec.decode("[1,2]"));
        assertEquals(Optional.empty(), codec.decode("{"));
    }
}
