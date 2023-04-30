/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.level;

public interface ICompanyLevel {
    String asComponent();

    int level();

    String levelName();

    ILevelRequirement requirement();

    ILevelSettings settings();
}
