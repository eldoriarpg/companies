/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.ConfirmMenu;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.admin.LevelActions;
import de.eldoria.companies.menus.admin.LevelField;
import de.eldoria.companies.menus.admin.LevelForm;
import de.eldoria.companies.menus.admin.LevelListMenu;
import de.eldoria.companies.menus.admin.LevelListView;
import de.eldoria.companies.menus.admin.LevelMenu;
import de.eldoria.companies.menus.admin.LevelView;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Runs the level editor: shows the level menus and performs what their buttons and the typed commands ask for.
 * <p>
 * Changes are refused on secondary nodes, which receive the levels from the primary node. A button refers to the
 * level it was shown for, not to a number, so a level that moved is still found and a removed one is refused.
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
            requireExisting(view.level());
            requirePrimary();
        } catch (CommandFailure failure) {
            show(viewer, view.rejected(entered, new LevelView.Rejection(Optional.empty(), failure)));
            return;
        }
        switch (LevelForm.parse(entered)) {
            case LevelForm.Invalid invalid ->
                show(
                        viewer,
                        view.rejected(
                                entered, new LevelView.Rejection(Optional.of(invalid.field()), invalid.failure())));
            case LevelForm.Valid valid -> {
                var level = view.level();
                valid.changes().accept(level);
                position.ifPresent(target -> {
                    if (target != level.level()) settings().moveLevel(level.level(), target);
                });
                deps.configuration().save();
                deps.adminLog()
                        .entry(viewer, "level save")
                        .with("level", level.level())
                        .with("name", level.levelName())
                        .write();
                info(viewer, level);
            }
        }
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
     * Adds a level and saves the configuration.
     *
     * @param admin    who adds the level
     * @param position position of the new level, clamped to the existing positions
     * @return the new level
     */
    CompanyLevel createLevel(CommandSender admin, int position) {
        requirePrimary();
        var level = settings().createLevel(position);
        deps.configuration().save();
        deps.adminLog()
                .entry(admin, "level create")
                .with("level", level.level())
                .write();
        return level;
    }

    /**
     * Changes one field of a level and saves the configuration.
     *
     * @param admin who changes the level
     * @param level the level
     * @param field name of the field
     * @param value entered value
     */
    void edit(CommandSender admin, CompanyLevel level, String field, String value) {
        requirePrimary();
        LevelField.byKey(field)
                .orElseThrow(() -> CommandFailure.actionBar("error.unkownField"))
                .apply(level, value);
        deps.configuration().save();
        deps.adminLog()
                .entry(admin, "level edit")
                .with("level", level.level())
                .with("field", field)
                .with("value", value)
                .write();
    }

    /**
     * Moves a level and saves the configuration.
     *
     * @param admin  who moves the level
     * @param source number of the level
     * @param target position it moves to
     */
    void move(CommandSender admin, int source, int target) {
        requirePrimary();
        level(source);
        settings().moveLevel(source, target);
        deps.configuration().save();
        deps.adminLog()
                .entry(admin, "level move")
                .with("level", source)
                .with("to", target)
                .write();
    }

    /**
     * Removes a level and saves the configuration.
     *
     * @param admin who removes the level
     * @param level number of the level
     */
    void remove(CommandSender admin, int level) {
        requirePrimary();
        Checks.isTrue(settings().deleteLevel(level), "error.invalidLevel");
        deps.configuration().save();
        deps.adminLog().entry(admin, "level remove").with("level", level).write();
    }

    /**
     * @param level number of the level
     * @return the level
     */
    CompanyLevel level(int level) {
        return settings().level(level).orElseThrow(() -> CommandFailure.actionBar("error.invalidLevel"));
    }

    private void removeConfirmed(Player viewer, CompanyLevel level) {
        try {
            requireExisting(level);
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

    private void requireExisting(CompanyLevel level) {
        Checks.isTrue(settings().level().contains(level), "error.invalidLevel");
    }

    private void requirePrimary() {
        Checks.isTrue(deps.configuration().nodeSettings().nodeType() == NodeType.PRIMARY, "error.secondarynode");
    }

    private void show(Player viewer, LevelView view) {
        deps.services().menus().show(viewer, new LevelMenu(deps.messages(), this), view);
    }

    private void showList(Player viewer, LevelListView view) {
        deps.services().menus().show(viewer, new LevelListMenu(deps.messages(), this), view);
    }

    private CompanySettings settings() {
        return deps.configuration().companySettings();
    }
}
