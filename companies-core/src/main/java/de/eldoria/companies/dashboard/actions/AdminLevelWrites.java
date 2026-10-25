/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.dashboard.views.Payloads;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.menus.admin.LevelField;
import de.eldoria.companies.services.LevelAdminService;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import tools.jackson.databind.JsonNode;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The dashboard's level editor, as {@code /companyadmin level} and {@code /companyadmin calclevel} offer it: the
 * levels, one level, a new level, a level saved from one form, a level moved or removed, and every company's level
 * recalculated. Editing needs the permission of {@code /companyadmin level}, recalculating the one of
 * {@code calclevel}. Changes run on the main thread through {@link LevelAdminService}, which refuses them on a
 * secondary node and shares them with the other nodes.
 * <p>
 * A level is named by its number in {@code level}. Creating takes an optional {@code position}, moving the target
 * position in {@code to}, and saving the entered text of each field by its key in {@code values} (as
 * {@code /companyadmin level edit} names them) with an optional {@code position}; {@code null} or an empty text clears
 * the tax and the alert limit, and the name must not be empty. One invalid value changes nothing and is refused with
 * the field's label in front of the reason. The methods block and are called off the main thread.
 */
public final class AdminLevelWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_LEVELS_LIST,
            Actions.ADMIN_LEVELS_INFO,
            Actions.ADMIN_LEVELS_CREATE,
            Actions.ADMIN_LEVELS_EDIT,
            Actions.ADMIN_LEVELS_MOVE,
            Actions.ADMIN_LEVELS_REMOVE,
            Actions.ADMIN_LEVELS_RECALCULATE);

    private static final String INVALID = "dashboard.invalid";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the levels are read and changed with
     */
    public AdminLevelWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs a level action.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        var permission = Actions.ADMIN_LEVELS_RECALCULATE.equals(request.name())
                ? Permission.Admin.CALC_LEVEL
                : Permission.Admin.LEVEL;
        if (!session.permissions().contains(permission)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        try {
            return onMainThread(() -> switch (request.name()) {
                case Actions.ADMIN_LEVELS_LIST -> list(request);
                case Actions.ADMIN_LEVELS_INFO -> level(request, levels().level(number(args, "level")));
                case Actions.ADMIN_LEVELS_CREATE -> create(request, admin, args);
                case Actions.ADMIN_LEVELS_EDIT -> edit(request, admin, args);
                case Actions.ADMIN_LEVELS_MOVE -> {
                    levels().move(admin, number(args, "level"), number(args, "to"));
                    yield list(request);
                }
                case Actions.ADMIN_LEVELS_REMOVE -> {
                    levels().remove(admin, number(args, "level"));
                    yield list(request);
                }
                case Actions.ADMIN_LEVELS_RECALCULATE -> recalculate(request, admin);
                default -> answers.refused(request, "dashboard.unsupported");
            });
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
    }

    private ActionResult list(ActionRequest request) {
        return answers.data(
                request,
                new Payloads.AdminLevels(
                        levels().editable(),
                        levels().levels().stream()
                                .map(AdminLevelWrites::payload)
                                .toList()));
    }

    private ActionResult level(ActionRequest request, CompanyLevel level) {
        return answers.data(request, payload(level));
    }

    private ActionResult create(ActionRequest request, String admin, JsonNode args) {
        var position = args.has("position")
                ? number(args, "position")
                : levels().levels().size() + 1;
        return level(request, levels().create(admin, position));
    }

    private ActionResult edit(ActionRequest request, String admin, JsonNode args) {
        var level = levels().level(number(args, "level"));
        var position = args.has("position") ? OptionalInt.of(number(args, "position")) : OptionalInt.empty();
        var invalid = levels().save(admin, level, entered(args.path("values")), position);
        if (invalid.isEmpty()) return level(request, level);
        var failure = invalid.get().failure();
        var label =
                sources.render().apply(CommandFailure.of(invalid.get().field().labelKey()));
        return answers.refused(
                request, failure.localeKey(), label + ": " + sources.render().apply(failure));
    }

    private ActionResult recalculate(ActionRequest request, String admin) {
        sources.recalculate().accept(() -> {});
        sources.log().entry(admin, "calclevel").write();
        return answers.data(request, answers.empty());
    }

    private static Map<LevelField, String> entered(JsonNode values) {
        if (!values.isObject()) throw CommandFailure.of(INVALID);
        var entered = new EnumMap<LevelField, String>(LevelField.class);
        for (var entry : values.properties()) {
            var field = LevelField.byKey(entry.getKey()).orElseThrow(() -> CommandFailure.of("error.unkownField"));
            var value = entry.getValue();
            if (!value.isValueNode()) throw CommandFailure.of(INVALID);
            if (field == LevelField.NAME && (value.isNull() || value.asString().isBlank())) {
                throw CommandFailure.of(INVALID);
            }
            entered.put(field, value.isNull() ? "" : value.asString());
        }
        return entered;
    }

    private static int number(JsonNode args, String field) {
        var value = args.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw CommandFailure.of(INVALID);
        return value.asInt();
    }

    private static Payloads.AdminLevel payload(CompanyLevel level) {
        var requirement = level.requirement();
        var settings = level.settings();
        return new Payloads.AdminLevel(
                level.level(),
                level.levelName(),
                requirement.orderCount(),
                requirement.memberCount(),
                requirement.earnedMoney(),
                requirement.deliveredItems(),
                settings.maxMembers(),
                settings.maxOrders(),
                settings.maxOrderSize(),
                settings.deliveryHoursBonus(),
                settings.taxPercent().orElse(null),
                settings.maxAlerts().orElse(null));
    }

    private ActionResult onMainThread(Supplier<ActionResult> action) {
        try {
            return CompletableFuture.supplyAsync(action, sources.mainThread()).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof CommandFailure failure) throw failure;
            throw e;
        }
    }

    private LevelAdminService levels() {
        return sources.levels();
    }

    /**
     * What the levels are read and changed with.
     *
     * @param levels      changes the levels and shares them with the other nodes
     * @param recalculate recalculates the level of every company, then runs the given task
     * @param mainThread  runs tasks on the main thread
     * @param log         the admin log
     * @param render      renders a refusal as plain text
     */
    public record Sources(
            LevelAdminService levels,
            Consumer<Runnable> recalculate,
            Executor mainThread,
            AdminLog log,
            Function<CommandFailure, String> render) {}
}
