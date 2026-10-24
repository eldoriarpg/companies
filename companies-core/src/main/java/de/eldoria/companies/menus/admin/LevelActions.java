/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.OptionalInt;

/**
 * What the buttons of the level editor do. Each action checks again that the level still exists and that levels may
 * be edited on this node, since a menu may be older than the configuration.
 */
public interface LevelActions {
    /**
     * Shows the list of levels.
     *
     * @param viewer player who clicked
     */
    void list(Player viewer);

    /**
     * Shows the editor of a level.
     *
     * @param viewer player who clicked
     * @param level  the level
     */
    void info(Player viewer, CompanyLevel level);

    /**
     * Adds a level and shows its editor.
     *
     * @param viewer   player who clicked
     * @param position position of the new level, clamped to the existing positions
     */
    void create(Player viewer, int position);

    /**
     * Applies the entered values and the chosen position to a level.
     *
     * @param viewer   player who clicked
     * @param view     the editor the values were entered in
     * @param entered  the text of each field
     * @param position the chosen position, if one was chosen
     */
    void save(Player viewer, LevelView view, Map<LevelField, String> entered, OptionalInt position);

    /**
     * Asks to confirm the removal of a level and removes it once confirmed.
     *
     * @param viewer player who clicked
     * @param level  the level
     */
    void remove(Player viewer, CompanyLevel level);
}
