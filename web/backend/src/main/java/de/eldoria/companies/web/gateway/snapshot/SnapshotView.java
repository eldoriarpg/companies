/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import tools.jackson.databind.JsonNode;

/**
 * A session's snapshot as the browser gets it.
 *
 * @param revision the snapshot's revision; each patch pushed on the stream raises it by one
 * @param snapshot the snapshot: {@code player}, {@code company}, {@code orders} and {@code rankings}, as the plugin
 * built them
 */
public record SnapshotView(long revision, JsonNode snapshot) {}
