/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.Message;
import tools.jackson.databind.node.ObjectNode;

/**
 * A node's answer to a request: the message as read by this version, and the JSON as the node sent it, with the
 * fields this version does not know. The gateway passes the raw form on, so a newer plugin's additions reach the web
 * app.
 *
 * @param message the answer as read
 * @param raw the answer as sent
 */
public record Reply(Message message, ObjectNode raw) {}
