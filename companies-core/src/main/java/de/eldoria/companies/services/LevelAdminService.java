/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.admin.LevelField;
import de.eldoria.companies.menus.admin.LevelForm;
import de.eldoria.companies.util.AdminLog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The company levels as admins change them, from the level menus, {@code /companyadmin level} and the dashboard alike.
 * <p>
 * Levels change on the primary node only; a secondary refuses with {@code error.secondarynode}. Every change saves
 * {@code company.yml}, writes the admin log and then, off the calling thread, writes the database copy and tells the
 * secondaries to take it, so no manual {@code /companyadmin syncconfiguration} is needed.
 */
public final class LevelAdminService {
    private final Configuration configuration;
    private final Runnable share;
    private final AdminLog log;
    private final Executor background;
    private final Logger logger;

    /**
     * @param configuration the configuration holding the levels
     * @param share         writes the database copy of the levels and tells the secondaries to take it
     * @param log           the admin log
     * @param background    where the database copy is written
     * @param logger        where a failed copy is reported
     */
    public LevelAdminService(
            Configuration configuration, Runnable share, AdminLog log, Executor background, Logger logger) {
        this.configuration = configuration;
        this.share = share;
        this.log = log;
        this.background = background;
        this.logger = logger;
    }

    /**
     * @return the levels, lowest first
     */
    public List<CompanyLevel> levels() {
        return settings().level();
    }

    /**
     * @param level number of the level
     * @return the level
     * @throws CommandFailure {@code error.invalidLevel} when there is no such level
     */
    public CompanyLevel level(int level) {
        return settings().level(level).orElseThrow(() -> CommandFailure.actionBar("error.invalidLevel"));
    }

    /**
     * Adds a level.
     *
     * @param admin    name of the admin
     * @param position position of the new level, clamped to the existing positions
     * @return the new level
     */
    public CompanyLevel create(String admin, int position) {
        requirePrimary();
        var level = settings().createLevel(position);
        changed();
        log.entry(admin, "level create").with("level", level.level()).write();
        return level;
    }

    /**
     * Saves a level form: every entered value and the chosen position. One invalid value changes nothing.
     *
     * @param admin    name of the admin
     * @param level    the level the form was shown for
     * @param entered  text of each entered field
     * @param position the chosen position, if any
     * @return the field holding an invalid value, or empty when the level was saved
     * @throws CommandFailure {@code error.invalidLevel} when the level was removed meanwhile
     */
    public Optional<LevelForm.Invalid> save(
            String admin, CompanyLevel level, Map<LevelField, String> entered, OptionalInt position) {
        requireExisting(level);
        requirePrimary();
        return switch (LevelForm.parse(entered)) {
            case LevelForm.Invalid invalid -> Optional.of(invalid);
            case LevelForm.Valid valid -> {
                valid.changes().accept(level);
                position.ifPresent(target -> {
                    if (target != level.level()) settings().moveLevel(level.level(), target);
                });
                changed();
                log.entry(admin, "level save")
                        .with("level", level.level())
                        .with("name", level.levelName())
                        .write();
                yield Optional.empty();
            }
        };
    }

    /**
     * Changes one field of a level.
     *
     * @param admin name of the admin
     * @param level the level
     * @param field name of the field
     * @param value entered value
     */
    public void edit(String admin, CompanyLevel level, String field, String value) {
        requirePrimary();
        LevelField.byKey(field)
                .orElseThrow(() -> CommandFailure.actionBar("error.unkownField"))
                .apply(level, value);
        changed();
        log.entry(admin, "level edit")
                .with("level", level.level())
                .with("field", field)
                .with("value", value)
                .write();
    }

    /**
     * Moves a level.
     *
     * @param admin  name of the admin
     * @param source number of the level
     * @param target position it moves to
     */
    public void move(String admin, int source, int target) {
        requirePrimary();
        level(source);
        settings().moveLevel(source, target);
        changed();
        log.entry(admin, "level move").with("level", source).with("to", target).write();
    }

    /**
     * Removes a level.
     *
     * @param admin name of the admin
     * @param level number of the level
     */
    public void remove(String admin, int level) {
        requirePrimary();
        Checks.isTrue(settings().deleteLevel(level), "error.invalidLevel");
        changed();
        log.entry(admin, "level remove").with("level", level).write();
    }

    /**
     * @param level a level shown earlier
     * @throws CommandFailure {@code error.invalidLevel} when it was removed meanwhile
     */
    public void requireExisting(CompanyLevel level) {
        Checks.isTrue(settings().level().contains(level), "error.invalidLevel");
    }

    /**
     * @return whether levels can be changed on this node, which is the primary
     */
    public boolean editable() {
        return configuration.nodeSettings().nodeType() == NodeType.PRIMARY;
    }

    private void changed() {
        configuration.save();
        CompletableFuture.runAsync(share, background).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not share the changed levels with the other nodes.", failure);
            return null;
        });
    }

    private void requirePrimary() {
        Checks.isTrue(editable(), "error.secondarynode");
    }

    private CompanySettings settings() {
        return configuration.companySettings();
    }
}
