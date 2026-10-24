/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * A colour of the server's palette, which a company may choose for its profile.
 *
 * @param name the palette entry, as the profile stores it, e.g. {@code dark_aqua}
 * @param hex  the colour as a hex string
 */
public record PaletteColor(String name, String hex) {}
