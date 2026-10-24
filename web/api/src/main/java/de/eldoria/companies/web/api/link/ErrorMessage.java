/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.link;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

/**
 * Either direction: a message was refused. Its id is the id of the refused message.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param code why it was refused
 * @param message details for the log
 */
@JsonTypeName("error")
public record ErrorMessage(
        @Nullable String id,
        int v,
        ErrorCode code,
        @Nullable String message) implements Message {}
