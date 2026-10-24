/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.link;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Why a message was refused.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum ErrorCode {
    /**
     * The message type is unknown to the receiver.
     */
    UNKNOWN_TYPE,
    /**
     * The message could not be read or misses required values.
     */
    INVALID,
    /**
     * The receiver does not speak the protocol version.
     */
    UNSUPPORTED_PROTOCOL,
    /**
     * The server's key or token was refused.
     */
    UNAUTHORIZED,
    /**
     * The plugin version is discontinued; nothing but status is accepted.
     */
    DISCONTINUED,
    /**
     * The session does not exist or has ended.
     */
    UNKNOWN_SESSION,
    /**
     * Too many messages in a short time.
     */
    RATE_LIMITED,
    /**
     * The receiver failed to handle the message.
     */
    INTERNAL,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
