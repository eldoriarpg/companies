/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

import java.util.UUID;

/**
 * An area a player stored under a name, to post area jobs on it in game or on the dashboard.
 *
 * @param name its name, unique per player
 * @param node the server whose world holds it
 * @param area its block box
 */
public record StoredArea(String name, UUID node, JobArea area) {}
