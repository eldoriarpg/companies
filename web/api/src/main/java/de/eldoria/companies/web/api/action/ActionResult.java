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

/**
 * Plugin to gateway: the outcome of an {@link ActionRequest}.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param ok whether the action succeeded
 * @param reason the message key of the refusal
 * @param message the refusal rendered by the plugin as plain text
 * @param data what a reading action returns, e.g. a page of search results
 */
@JsonTypeName("action.result")
public record ActionResult(
        @Nullable String id,
        int v,
        boolean ok,
        @Nullable String reason,
        @Nullable String message,
        @Nullable JsonNode data)
        implements Message {}
