/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Plugin to gateway: the plugin knows the session and the player was told about the login; the gateway binds the
 * browser.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param admin whether the player holds {@code companies.admin} or any {@code companies.admin.*} permission, which shows
 * the server's deprecation notices; none from plugins before it was added, read as {@code false}
 * @param adminFeatures the admin features the player's permissions allow, which the gateway offers the browser
 * instead of every admin feature of the server; none from plugins before it was added, which leaves every admin
 * feature to an admin
 */
@JsonTypeName("session.confirmed")
public record SessionConfirmed(
        @Nullable String id,
        int v,
        String session,
        @Nullable Boolean admin,
        @Nullable List<String> adminFeatures) implements Message {
    /**
     * A confirmation without the admin flag and admin features, as plugins before it was added send it.
     *
     * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
     * @param v the protocol version, see {@link Message#v()}
     * @param session the session id
     */
    public SessionConfirmed(@Nullable String id, int v, String session) {
        this(id, v, session, null, null);
    }
}
