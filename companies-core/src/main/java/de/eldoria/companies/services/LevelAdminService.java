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

import java.util.Arrays;
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
 * {@code company.yml}, takes its database copy on the calling thread and writes the admin log; the copy is then
 * written and the secondaries told, one change after the other, so a later change never lands before an earlier one.
 * A change that changes nothing is neither saved nor logged. The last level stays.
 */
public final class LevelAdminService {
    private final Configuration configuration;
    private final Share share;
    private final AdminLog log;
    private final Executor background;
    private final Logger logger;

    /**
     * @param configuration the configuration holding the levels
     * @param share         takes the database copy of the levels and returns the task writing it
     * @param log           the admin log
     * @param background    writes the database copies one after the other
     * @param logger        where a failed copy is reported
     */
    public LevelAdminService(
            Configuration configuration, Share share, AdminLog log, Executor background, Logger logger) {
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
        changed(admin);
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
                var number = level.level();
                var before = inputs(level);
                valid.changes().accept(level);
                position.ifPresent(target -> settings().moveLevel(level.level(), target));
                if (level.level() == number && inputs(level).equals(before)) yield Optional.empty();
                changed(admin);
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
        changed(admin);
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
        var level = level(source);
        settings().moveLevel(source, target);
        if (level.level() == source) return;
        changed(admin);
        log.entry(admin, "level move")
                .with("level", source)
                .with("to", level.level())
                .write();
    }

    /**
     * Removes a level.
     *
     * @param admin name of the admin
     * @param level number of the level
     */
    public void remove(String admin, int level) {
        requirePrimary();
        level(level);
        Checks.isTrue(settings().level().size() > 1, "companyadmin.level.lastLevel");
        settings().deleteLevel(level);
        changed(admin);
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
     * @return why levels cannot be changed on this node: {@code secondary} or {@code unreadable}, none when they can
     */
    public Optional<String> blocked() {
        if (configuration.nodeSettings().nodeType() != NodeType.PRIMARY) return Optional.of("secondary");
        if (configuration.unreadable(Configuration.COMPANY_SETTINGS)) return Optional.of("unreadable");
        return Optional.empty();
    }

    /**
     * @return whether levels can be changed on this node, which is the primary while it can read {@code company.yml}
     */
    public boolean editable() {
        return blocked().isEmpty();
    }

    private static List<String> inputs(CompanyLevel level) {
        return Arrays.stream(LevelField.values())
                .map(field -> field.input(level))
                .toList();
    }

    private void changed(String admin) {
        configuration.save();
        try {
            CompletableFuture.runAsync(share.prepare(admin), background).exceptionally(this::notShared);
        } catch (RuntimeException e) {
            notShared(e);
        }
    }

    private Void notShared(Throwable failure) {
        logger.log(Level.SEVERE, "Could not share the changed levels with the other nodes.", failure);
        return null;
    }

    private void requirePrimary() {
        Checks.isTrue(configuration.nodeSettings().nodeType() == NodeType.PRIMARY, "error.secondarynode");
        Checks.isTrue(!configuration.unreadable(Configuration.COMPANY_SETTINGS), "companyadmin.level.unreadable");
    }

    private CompanySettings settings() {
        return configuration.companySettings();
    }

    /**
     * Shares changed levels with the other nodes.
     */
    @FunctionalInterface
    public interface Share {
        /**
         * Takes the database copy of the levels as they are now; called on the thread that changed them.
         *
         * @param admin who changed them, stored with the new revision
         * @return writes the copy and tells the secondaries; runs off that thread
         */
        Runnable prepare(String admin);
    }
}
