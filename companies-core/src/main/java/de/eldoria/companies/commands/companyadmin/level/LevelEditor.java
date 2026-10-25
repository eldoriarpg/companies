/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.ConfirmMenu;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.admin.LevelActions;
import de.eldoria.companies.menus.admin.LevelField;
import de.eldoria.companies.menus.admin.LevelListMenu;
import de.eldoria.companies.menus.admin.LevelListView;
import de.eldoria.companies.menus.admin.LevelMenu;
import de.eldoria.companies.menus.admin.LevelView;
import de.eldoria.companies.services.LevelAdminService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Runs the level editor: shows the level menus and performs what their buttons and the typed commands ask for.
 * <p>
 * Changes go through {@link LevelAdminService}, which refuses them on secondary nodes and shares them with the other
 * nodes. A button refers to the level it was shown for, not to a number, so a level that moved is still found and a
 * removed one is refused.
 */
public final class LevelEditor implements LevelActions {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public LevelEditor(CommandDependencies deps) {
        this.deps = deps;
    }

    @Override
    public void list(Player viewer) {
        showList(viewer, LevelListView.of(settings()));
    }

    @Override
    public void info(Player viewer, CompanyLevel level) {
        show(viewer, LevelView.of(level, settings().level().size()));
    }

    @Override
    public void create(Player viewer, int position) {
        try {
            info(viewer, createLevel(viewer, position));
        } catch (CommandFailure failure) {
            showList(viewer, LevelListView.of(settings()).rejected(failure));
        }
    }

    @Override
    public void save(Player viewer, LevelView view, Map<LevelField, String> entered, OptionalInt position) {
        try {
            var invalid = levels().save(viewer.getName(), view.level(), entered, position);
            if (invalid.isPresent()) {
                show(
                        viewer,
                        view.rejected(
                                entered,
                                new LevelView.Rejection(
                                        Optional.of(invalid.get().field()),
                                        invalid.get().failure())));
                return;
            }
        } catch (CommandFailure failure) {
            show(viewer, view.rejected(entered, new LevelView.Rejection(Optional.empty(), failure)));
            return;
        }
        info(viewer, view.level());
    }

    @Override
    public void remove(Player viewer, CompanyLevel level) {
        var question = MenuText.create()
                .locale(
                        "companyadmin.level.removeConfirm",
                        Values.number("level_number", level.level()),
                        Values.text("level_name", level.levelName()));
        deps.services()
                .menus()
                .show(
                        viewer,
                        new ConfirmMenu(deps.plugin()),
                        new ConfirmMenu.Prompt(
                                question,
                                confirmed -> removeConfirmed(confirmed, level),
                                denied -> back(denied, level)));
    }

    /**
     * Adds a level.
     *
     * @param admin    who adds the level
     * @param position position of the new level, clamped to the existing positions
     * @return the new level
     */
    CompanyLevel createLevel(CommandSender admin, int position) {
        return levels().create(admin.getName(), position);
    }

    /**
     * Changes one field of a level.
     *
     * @param admin who changes the level
     * @param level the level
     * @param field name of the field
     * @param value entered value
     */
    void edit(CommandSender admin, CompanyLevel level, String field, String value) {
        levels().edit(admin.getName(), level, field, value);
    }

    /**
     * Moves a level.
     *
     * @param admin  who moves the level
     * @param source number of the level
     * @param target position it moves to
     */
    void move(CommandSender admin, int source, int target) {
        levels().move(admin.getName(), source, target);
    }

    /**
     * Removes a level.
     *
     * @param admin who removes the level
     * @param level number of the level
     */
    void remove(CommandSender admin, int level) {
        levels().remove(admin.getName(), level);
    }

    /**
     * @param level number of the level
     * @return the level
     */
    CompanyLevel level(int level) {
        return levels().level(level);
    }

    private void removeConfirmed(Player viewer, CompanyLevel level) {
        try {
            levels().requireExisting(level);
            remove(viewer, level.level());
            list(viewer);
        } catch (CommandFailure failure) {
            showList(viewer, LevelListView.of(settings()).rejected(failure));
        }
    }

    private void back(Player viewer, CompanyLevel level) {
        if (settings().level().contains(level)) {
            info(viewer, level);
            return;
        }
        list(viewer);
    }

    private void show(Player viewer, LevelView view) {
        deps.services().menus().show(viewer, new LevelMenu(deps.messages(), this), view);
    }

    private void showList(Player viewer, LevelListView view) {
        deps.services().menus().show(viewer, new LevelListMenu(deps.messages(), this), view);
    }

    private LevelAdminService levels() {
        return deps.levels();
    }

    private CompanySettings settings() {
        return deps.configuration().companySettings();
    }
}
