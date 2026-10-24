/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.action;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Gateway to plugin: a player's action from the web; answered with {@link ActionResult}. The plugin checks the session
 * and runs the action as that player with the same checks as the command.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param player the session's player
 * @param name the action, see {@link de.eldoria.companies.web.api.Actions}
 * @param args the action's arguments as an object
 */
@JsonTypeName("action")
public record ActionRequest(@Nullable String id, int v, String session, UUID player, String name, JsonNode args)
        implements Message {}
