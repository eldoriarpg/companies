/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.Heartbeat;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.nodes.NodeMessage.NodeStopped;
import de.eldoria.companies.nodes.NodeMessage.OfferAnnounced;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import de.eldoria.companies.nodes.NodeMessage.RankingChanged;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import de.eldoria.companies.nodes.NodeMessage.SettingsChanged;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes and reads the JSON of node messages.
 * <p>
 * A message is one flat JSON object: the envelope fields {@code v} (the format version), {@code type} and
 * {@code node} (the uid of the sending node), followed by the fields of the message, for example
 * {@code {"v":1,"type":"inbox.new","node":"…","entry":12,"player":"…"}}. Readers skip messages of another format
 * version, of an unknown type and anything they cannot parse, and ignore unknown fields, so a node running a newer
 * version does not break the others. The version only changes when a message can no longer be read the old way.
 */
public final class NodeMessageCodec {
    /**
     * The format version written and read.
     */
    public static final int VERSION = 1;

    private static final String VERSION_FIELD = "v";
    private static final String TYPE_FIELD = "type";
    private static final String NODE_FIELD = "node";

    private static final Map<Class<? extends NodeMessage>, String> TYPES = Map.of(
            InboxNew.class, "inbox.new",
            PlayerPresence.class, "player.presence",
            CacheInvalidate.class, "cache.invalidate",
            RankingChanged.class, "ranking.changed",
            BoardsChanged.class, "boards.changed",
            SettingsChanged.class, "settings.changed",
            Heartbeat.class, "node.heartbeat",
            NodeStopped.class, "node.stopped",
            OfferAnnounced.class, "offer.announced",
            ServerEventChanged.class, "event.changed");
    private static final Map<String, Class<? extends NodeMessage>> CLASSES =
            TYPES.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    /**
     * The type name of a message class, which is also the last part of its channel.
     *
     * @param type message class
     * @return the type name, for example {@code inbox.new}
     */
    public static String type(Class<? extends NodeMessage> type) {
        return TYPES.get(type);
    }

    /**
     * Writes a message.
     *
     * @param node    uid of the sending node
     * @param message the message
     * @return the JSON text
     */
    public String encode(UUID node, NodeMessage message) {
        var json = mapper.createObjectNode();
        json.put(VERSION_FIELD, VERSION);
        json.put(TYPE_FIELD, type(message.getClass()));
        json.put(NODE_FIELD, node.toString());
        json.setAll((ObjectNode) mapper.valueToTree(message));
        return mapper.writeValueAsString(json);
    }

    /**
     * Reads a message.
     *
     * @param text the JSON text
     * @return the message with its sender, empty when it has another format version, an unknown type or cannot be
     *     read
     */
    public Optional<Envelope> decode(String text) {
        try {
            if (!(mapper.readTree(text) instanceof ObjectNode json)) return Optional.empty();
            if (json.path(VERSION_FIELD).asInt(-1) != VERSION) return Optional.empty();
            var type = CLASSES.get(json.path(TYPE_FIELD).asString(""));
            if (type == null) return Optional.empty();
            var node = UUID.fromString(json.path(NODE_FIELD).asString(""));
            json.remove(VERSION_FIELD);
            json.remove(TYPE_FIELD);
            json.remove(NODE_FIELD);
            return Optional.of(new Envelope(node, mapper.treeToValue(json, type)));
        } catch (JacksonException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * A message read together with the node that sent it.
     *
     * @param node    uid of the sending node
     * @param message the message
     */
    public record Envelope(UUID node, NodeMessage message) {}
}
