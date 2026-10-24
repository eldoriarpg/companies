/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.BrandingUpdate;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.Pong;
import de.eldoria.companies.web.api.link.UnknownMessage;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionConfirmed;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.session.SessionOpened;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.Subscribe;
import de.eldoria.companies.web.api.update.Unsubscribe;
import org.jspecify.annotations.Nullable;

/**
 * A message on the connection between a node and the gateway: the envelope {@code {"type", "id", "v", …}}.
 * <p>
 * {@code type} names the message and is written by Jackson from the record's type name. A type this version does not
 * know reads as {@link UnknownMessage} instead of failing. The envelope only ever gains optional fields.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = UnknownMessage.class)
@JsonSubTypes({
    @JsonSubTypes.Type(Hello.class),
    @JsonSubTypes.Type(Welcome.class),
    @JsonSubTypes.Type(BrandingUpdate.class),
    @JsonSubTypes.Type(CreateSession.class),
    @JsonSubTypes.Type(SessionCreated.class),
    @JsonSubTypes.Type(SessionOpened.class),
    @JsonSubTypes.Type(SessionConfirmed.class),
    @JsonSubTypes.Type(SessionClosed.class),
    @JsonSubTypes.Type(SessionsRevoked.class),
    @JsonSubTypes.Type(SnapshotRequest.class),
    @JsonSubTypes.Type(Snapshot.class),
    @JsonSubTypes.Type(Subscribe.class),
    @JsonSubTypes.Type(Unsubscribe.class),
    @JsonSubTypes.Type(Patch.class),
    @JsonSubTypes.Type(ActionRequest.class),
    @JsonSubTypes.Type(ActionResult.class),
    @JsonSubTypes.Type(Ping.class),
    @JsonSubTypes.Type(Pong.class),
    @JsonSubTypes.Type(ErrorMessage.class)
})
public interface Message {
    /**
     * The id of the message. Requests carry one; their answer, or the {@link ErrorMessage} refusing them, carries the
     * same id. Pushed messages such as patches carry none.
     *
     * @return the id, none for pushed messages
     */
    @Nullable
    String id();

    /**
     * The protocol version the message is written in, see {@link Protocol#VERSION}.
     *
     * @return the protocol version
     */
    int v();
}
