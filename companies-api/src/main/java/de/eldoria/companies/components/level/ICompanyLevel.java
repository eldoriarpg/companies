/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.level;

/**
 * A configured company level.
 */
public interface ICompanyLevel {
    /**
     * The level and its requirements as MiniMessage text with locale keys of Companies.
     *
     * @return text for display
     */
    String asComponent();

    /**
     * The number of the level.
     *
     * @return level, starting at 1
     */
    int level();

    /**
     * The display name of the level.
     *
     * @return name
     */
    String levelName();

    /**
     * What a company needs to reach the level.
     *
     * @return requirement
     */
    ILevelRequirement requirement();

    /**
     * What a company on this level may do.
     *
     * @return settings
     */
    ILevelSettings settings();
}
