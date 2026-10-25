/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.nodes;

import java.util.UUID;

/**
 * The revision of a shared file a node applied last.
 *
 * @param node     the node
 * @param path     the file
 * @param revision the revision
 */
public record AppliedSettings(UUID node, String path, int revision) {}
