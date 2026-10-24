/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The first message of a node on a new connection; the gateway answers with {@link Welcome}. Its {@code v} is the
 * highest protocol version the plugin knows.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param plugin the plugin version, e.g. {@code 2.2.0}
 * @param schema the database schema version, e.g. {@code 1.9}
 * @param node the node that connects
 * @param features the features the node offers, see {@link de.eldoria.companies.web.api.Features}; the web app shows
 * only these
 * @param limits the node's limits
 * @param credentials the server's key or token
 * @param userAgent {@code Companies/<version>}, also sent as the connection's {@code User-Agent} header
 * @param branding the server's name and icon for the dashboard; none for the default look
 */
@JsonTypeName("hello")
public record Hello(
        @Nullable String id,
        int v,
        String plugin,
        String schema,
        NodeInfo node,
        List<String> features,
        Limits limits,
        Credentials credentials,
        String userAgent,
        @Nullable Branding branding)
        implements Message {}
