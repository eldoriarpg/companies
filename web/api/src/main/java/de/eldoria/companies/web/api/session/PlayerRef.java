/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import java.util.UUID;

/**
 * A player by id and name.
 *
 * @param uuid the player's id
 * @param name the player's name
 */
public record PlayerRef(UUID uuid, String name) {}
