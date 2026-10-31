/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

/**
 * The home of a company, where its members teleport to with {@code /company home}.
 *
 * @param world  the world
 * @param x      the block x
 * @param y      the block y
 * @param z      the block z
 * @param server the name of its server in the proxy, none when the server has none
 */
public record HomeView(
        String world, int x, int y, int z, @Nullable String server) {}
