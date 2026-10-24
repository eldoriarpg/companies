/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/**
 * Writes the JSON body of a webhook request.
 * <p>
 * Mentions are always disabled, so no text in an embed can ping anyone.
 */
public final class WebhookPayload {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Optional<String> username;
    private final Optional<String> avatarUrl;

    /**
     * @param username  the name to post under, empty to keep the webhook's own
     * @param avatarUrl the avatar to post with, empty to keep the webhook's own
     */
    public WebhookPayload(Optional<String> username, Optional<String> avatarUrl) {
        this.username = username;
        this.avatarUrl = avatarUrl;
    }

    /**
     * Writes the body for the embeds.
     *
     * @param embeds at most ten embeds
     * @return the JSON body
     */
    public String write(List<Embed> embeds) {
        var root = mapper.createObjectNode();
        username.ifPresent(name -> root.put("username", name));
        avatarUrl.ifPresent(url -> root.put("avatar_url", url));
        root.putObject("allowed_mentions").putArray("parse");
        var array = root.putArray("embeds");
        embeds.forEach(embed -> write(array.addObject(), embed));
        return mapper.writeValueAsString(root);
    }

    private static void write(ObjectNode node, Embed embed) {
        node.put("title", embed.title());
        if (!embed.description().isEmpty()) node.put("description", embed.description());
        node.put("color", embed.color());
        node.put("timestamp", embed.timestamp().toString());
        var fields = node.putArray("fields");
        for (var field : embed.fields()) {
            fields.addObject()
                    .put("name", field.name())
                    .put("value", field.value())
                    .put("inline", field.inline());
        }
    }
}
