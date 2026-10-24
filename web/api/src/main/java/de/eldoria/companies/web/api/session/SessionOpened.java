/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Gateway to plugin: a browser entered the code of a pending session; the plugin answers with {@link SessionConfirmed},
 * or with an error for a session it did not create.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param player the player the session belongs to
 * @param device a short description of the browser, e.g. {@code Firefox on Android}
 */
@JsonTypeName("session.opened")
public record SessionOpened(
        @Nullable String id,
        int v,
        String session,
        UUID player,
        @Nullable String device) implements Message {}
