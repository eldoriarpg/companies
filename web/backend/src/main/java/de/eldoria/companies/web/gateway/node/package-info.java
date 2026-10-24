/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * The nodes' websocket: handshake, authentication with Lyna keys or server tokens, routing of requests to the
 * nodes of a server, and heartbeats.
 */
@NullMarked
@DefaultAnnotationForMethods(NonNull.class)
@DefaultAnnotationForParameters(NonNull.class)
package de.eldoria.companies.web.gateway.node;

import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
