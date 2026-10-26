/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.RulesEditor;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.system.GuessedLearning;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dashboard's view of the orders the server places itself, as {@code /companyadmin systemorders} offers it: the
 * system orders open or in work, why one was placed, what guessed orders learned per material, and the defined rules,
 * edited as a whole list by {@link RulesEditor}. The admin needs {@code companies.admin.orders}, and
 * {@code companies.admin.reload} too to save rules. Looking at a decision is written to the admin log under the admin's name with
 * {@code @dashboard}. The methods block and are called off the main thread.
 */
public final class AdminSystemOrderWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_SYSTEM_ORDERS_LIST,
            Actions.ADMIN_SYSTEM_ORDERS_INFO,
            Actions.ADMIN_SYSTEM_ORDERS_STATS,
            Actions.ADMIN_SYSTEM_ORDERS_RULES,
            Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the system orders are read with
     */
    public AdminSystemOrderWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an admin action on system orders.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.ORDERS)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        return switch (request.name()) {
            case Actions.ADMIN_SYSTEM_ORDERS_LIST -> answers.data(request, new Listing(list()));
            case Actions.ADMIN_SYSTEM_ORDERS_INFO ->
                info(request, session, args.path("order").asInt(0));
            case Actions.ADMIN_SYSTEM_ORDERS_STATS -> answers.data(request, new Statistics(stats()));
            case Actions.ADMIN_SYSTEM_ORDERS_RULES -> rules(request, session);
            case Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE -> save(request, session, args);
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private ActionResult rules(ActionRequest request, StoredSession session) {
        var rules = RulesEditor.read(sources.configuration(), sources.nodeData().get());
        var editable = session.permissions().contains(Permission.Admin.RELOAD);
        return answers.data(request, new RuleList(rules.revision(), editable, rules.rules()));
    }

    private ActionResult save(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.RELOAD)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        if (!args.path("base").canConvertToInt()) return answers.refused(request, "dashboard.invalid");
        var admin = session.playerName() + "@dashboard";
        var result = RulesEditor.save(
                sources.configuration(),
                sources.nodeData().get(),
                args.path("base").asInt(),
                args.path("rules"),
                sources.knows(),
                admin);
        return switch (result) {
            case RulesEditor.Result.Invalid invalid ->
                answers.data(request, new RulesSaved("invalid", 0, invalid.fields(), null));
            case RulesEditor.Result.Newer newer ->
                answers.data(request, new RulesSaved("newer", newer.latest(), Map.of(), newer.rules()));
            case RulesEditor.Result.Done done ->
                stored(request, admin, done.outcome(), args.path("rules").size());
        };
    }

    private ActionResult stored(ActionRequest request, String admin, Configuration.StoreOutcome outcome, int rules) {
        return switch (outcome) {
            case Configuration.StoreOutcome.Stored stored -> {
                sources.log()
                        .entry(admin, "system order rules save")
                        .with("revision", stored.revision())
                        .with("rules", rules)
                        .write();
                if (stored.replaced()) apply();
                yield answers.data(request, new RulesSaved("saved", stored.revision(), Map.of(), null));
            }
            case Configuration.StoreOutcome.Unchanged unchanged ->
                answers.data(request, new RulesSaved("unchanged", unchanged.latest(), Map.of(), null));
            case Configuration.StoreOutcome.Newer newer ->
                answers.data(request, new RulesSaved("newer", newer.latest(), Map.of(), null));
        };
    }

    private void apply() {
        try {
            sources.applyChanges().run();
        } catch (RuntimeException e) {
            sources.logger().log(Level.WARNING, "Could not schedule applying the saved system order rules.", e);
        }
    }

    private List<Row> list() {
        return sources.orders().systemDecisions(OrderState.UNCLAIMED, OrderState.CLAIMED).stream()
                .map(decision -> {
                    var order =
                            sources.orders().retrieveOrderById(decision.order()).join();
                    var simple = order == null ? null : order.orElse(null);
                    return new Row(
                            decision.order(),
                            simple == null ? decision.rule() : simple.name(),
                            decision.rule(),
                            decision.kind(),
                            simple == null ? null : simple.state().name(),
                            sources.instant().apply(decision.decidedAt()),
                            decision.paid(),
                            decision.account());
                })
                .toList();
    }

    private ActionResult info(ActionRequest request, StoredSession session, int id) {
        if (id <= 0) return answers.refused(request, "error.unknownOrder");
        var found = sources.orders().systemDecision(id);
        if (found.isEmpty()) return answers.refused(request, "companyadmin.systemorders.error.unknown");
        sources.log()
                .entry(session.playerName() + "@dashboard", "system order info")
                .with("id", id)
                .write();
        return answers.data(request, decision(found.get()));
    }

    private Decision decision(SystemDecision decision) {
        JsonNode record;
        try {
            record = MAPPER.readTree(decision.record());
        } catch (RuntimeException e) {
            record = null;
        }
        return new Decision(
                decision.order(),
                decision.rule(),
                decision.kind(),
                sources.instant().apply(decision.decidedAt()),
                decision.paid(),
                decision.account(),
                record);
    }

    private List<Learned> stats() {
        var guessed = sources.settings().get().guessed();
        var learning = guessed.learning();
        var since = sources.now().get().minusDays(learning.days());
        return GuessedLearning.stats(sources.orders(), guessed, since).values().stream()
                .sorted(Comparator.comparingInt(GuessedLearning.Stats::posted)
                        .reversed()
                        .thenComparing(GuessedLearning.Stats::material))
                .map(stats -> new Learned(
                        stats.material(),
                        stats.posted(),
                        stats.taken(),
                        stats.expired(),
                        stats.minutesToTake(),
                        learning.enabled() ? stats.factor() : guessed.factor(),
                        stats.offset(),
                        stats.takeRate(learning.weightFloor())))
                .toList();
    }

    /**
     * What the system orders are read with.
     *
     * @param orders        the order repository
     * @param settings      the current system order settings
     * @param instant       turns stored times into instants
     * @param now           the current time as stored
     * @param log           the admin log
     * @param configuration the configuration of this node, whose shared files the rules are saved to
     * @param nodeData      the node repository holding the revisions
     * @param applyChanges  applies a saved revision on this node
     * @param knows         whether a material names an item that can be ordered
     * @param logger        reports a revision that could not be applied
     * @param render        renders a refusal as plain text
     */
    public record Sources(
            AOrderData orders,
            Supplier<SystemOrderSettings> settings,
            Function<LocalDateTime, Instant> instant,
            Supplier<LocalDateTime> now,
            AdminLog log,
            Configuration configuration,
            Supplier<ANodeData> nodeData,
            Runnable applyChanges,
            Predicate<String> knows,
            Logger logger,
            Function<CommandFailure, String> render) {}

    /**
     * The defined rules as the latest revision has them.
     *
     * @param revision the revision, the base of the next save
     * @param editable whether the admin may change them
     * @param rules    the rules, as the file writes them
     */
    public record RuleList(int revision, boolean editable, JsonNode rules) {}

    /**
     * What came of saving rules.
     *
     * @param result   {@code saved}, {@code unchanged}, {@code newer} or {@code invalid}
     * @param revision the revision now latest, 0 when invalid
     * @param errors   the problems by dotted path, such as {@code rules.2.id}
     * @param rules    the rules of the newer revision, when one was stored meanwhile
     */
    public record RulesSaved(
            String result,
            int revision,
            Map<String, String> errors,
            @Nullable JsonNode rules) {}

    /**
     * The system orders open or in work.
     *
     * @param orders the orders, oldest first
     */
    public record Listing(List<Row> orders) {}

    /**
     * One system order open or in work.
     *
     * @param order   id of the order
     * @param name    name of the order
     * @param rule    the rule that placed it, {@code guessed} for a guessed order
     * @param kind    how it was decided, {@code DEFINED} or {@code GUESSED}
     * @param state   its state, null when it was removed meanwhile
     * @param placed  when it was placed
     * @param paid    what was put into it
     * @param account the economy account that paid it, null for minted money
     */
    public record Row(
            int order,
            String name,
            String rule,
            String kind,
            @Nullable String state,
            Instant placed,
            double paid,
            @Nullable String account) {}

    /**
     * Why a system order was placed.
     *
     * @param order   id of the order
     * @param rule    the rule that placed it
     * @param kind    how it was decided, {@code DEFINED} or {@code GUESSED}
     * @param placed  when it was placed
     * @param paid    what was put into it
     * @param account the economy account that paid it, null for minted money
     * @param record  the numbers the decision stood on, as stored; null when they cannot be read
     */
    public record Decision(
            int order,
            String rule,
            String kind,
            Instant placed,
            double paid,
            @Nullable String account,
            @Nullable JsonNode record) {}

    /**
     * What guessed orders learned.
     *
     * @param materials per material, most placed first
     */
    public record Statistics(List<Learned> materials) {}

    /**
     * What guessed orders of one material did.
     *
     * @param material      the material
     * @param posted        orders placed
     * @param taken         orders taken
     * @param expired       orders nobody took in time
     * @param minutesToTake average minutes until one was taken, null when none was
     * @param factor        the factor a new order of it pays now: the learned one, or the configured one while learning
     *                      is off
     * @param offset        how far learning moved the factor
     * @param takeRate      the share taken, smoothed and floored
     */
    public record Learned(
            String material,
            int posted,
            int taken,
            int expired,
            @Nullable Double minutesToTake,
            double factor,
            double offset,
            double takeRate) {}
}
