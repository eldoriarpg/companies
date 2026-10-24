/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;

import java.util.List;
import java.util.Optional;

/**
 * The model of the level list: the configured levels in their order.
 *
 * @param levels the levels, the first being level 1
 * @param error  why the last action was refused, shown on top
 */
public record LevelListView(List<CompanyLevel> levels, Optional<CommandFailure> error) {
    /**
     * @param levels the levels, the first being level 1
     * @param error  why the last action was refused, shown on top
     */
    public LevelListView {
        levels = List.copyOf(levels);
    }

    /**
     * @param settings company settings holding the levels
     * @return the list of the configured levels
     */
    public static LevelListView of(CompanySettings settings) {
        return new LevelListView(settings.level(), Optional.empty());
    }

    /**
     * @param failure why the last action was refused
     * @return the same list showing the failure
     */
    public LevelListView rejected(CommandFailure failure) {
        return new LevelListView(levels, Optional.of(failure));
    }

    /**
     * The positions a new level can take, the last one appending it.
     *
     * @return the highest position
     */
    public int lastPosition() {
        return levels.size() + 1;
    }
}
