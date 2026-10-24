/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * A pending invite of the player.
 *
 * @param company the inviting company's id
 * @param name its name
 */
public record InviteView(int company, String name) {}
