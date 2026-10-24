/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * A deprecated part of the protocol the server still uses, with the date it stops working.
 *
 * @param kind what kind of part it is
 * @param name the field as {@code type.path}, the message type, the feature name or the protocol version
 * @param since when it was deprecated
 * @param removedAt when the gateway stops reading and sending it
 * @param replacement what replaces it
 * @param message a plain sentence for admins
 */
public record DeprecatedPart(
        PartKind kind,
        String name,
        LocalDate since,
        LocalDate removedAt,
        @Nullable String replacement,
        @Nullable String message) {}
