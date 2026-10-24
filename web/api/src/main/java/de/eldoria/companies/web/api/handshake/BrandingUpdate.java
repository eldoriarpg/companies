/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

/**
 * Plugin to gateway: the server's branding changed, e.g. after {@code /companyadmin reload}, or the gateway answered
 * {@link Hello#branding()} with {@link Welcome#iconCached()} {@code false}. It replaces the branding of the hello.
 * The icon may be left out only when its hash is one the gateway confirmed it has.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param branding the new branding
 */
@JsonTypeName("branding.update")
public record BrandingUpdate(@Nullable String id, int v, Branding branding) implements Message {}
