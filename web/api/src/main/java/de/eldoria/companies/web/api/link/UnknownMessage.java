/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.link;

import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

/**
 * A message whose type this version does not know, read instead of failing. It is never sent; the receiver answers it
 * with {@link ErrorCode#UNKNOWN_TYPE}.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 */
public record UnknownMessage(@Nullable String id, int v) implements Message {}
