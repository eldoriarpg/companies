/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * An item as the web app shows it, never an item stack.
 *
 * @param material the material key, e.g. {@code minecraft:diamond}
 * @param name the display name as plain text
 * @param amount the amount
 * @param icon the icon key the web app maps to a sprite; unknown keys get a generic icon
 */
public record ItemView(String material, String name, int amount, String icon) {}
