/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.companyadmin.OffersCommand;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.offers.GoalDraft;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dashboard's view of the event offers, as {@code /companyadmin offers} offers it: the newest offers with their
 * state, progress and money, one offer with who created it, its money, its contributors or its ranking and prizes, and
 * creating a goal or a contest, pausing, resuming and cancelling an offer, changing how many chunks of a goal wait at
 * once and settling a prize stuck while being paid. A new offer is checked by {@link GoalDraft} and a change made by
 * the same steps as the command, so both refuse the same things. The admin needs {@code companies.admin.offers}.
 * Every change and every look at an offer is written to the admin log under the admin's name with {@code @dashboard},
 * and a change asks the primary to place and withdraw chunks soon. The methods block and are called off the main
 * thread.
 */
public final class AdminOfferWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_OFFERS_LIST,
            Actions.ADMIN_OFFERS_INFO,
            Actions.ADMIN_OFFERS_CREATE,
            Actions.ADMIN_OFFERS_PAUSE,
            Actions.ADMIN_OFFERS_RESUME,
            Actions.ADMIN_OFFERS_CANCEL,
            Actions.ADMIN_OFFERS_OPEN,
            Actions.ADMIN_OFFERS_PRIZE);

    /**
     * The most offers listed, the newest first.
     */
    static final int LISTED = 50;

    /**
     * The most contributors of a goal shown.
     */
    static final int CONTRIBUTORS = 10;

    /**
     * The most chunks of a goal that may wait at once, as the command allows.
     */
    static final int MAX_OPEN = GoalDraft.MAX_OPEN_CHUNKS;

    private static final String PREFIX = "companyadmin.offers.";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the offers are read and changed with
     */
    public AdminOfferWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an admin action on event offers.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.OFFERS)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        var id = args.path("offer").asInt(0);
        return switch (request.name()) {
            case Actions.ADMIN_OFFERS_LIST -> answers.data(request, new OfferList(list()));
            case Actions.ADMIN_OFFERS_INFO -> info(request, admin, id);
            case Actions.ADMIN_OFFERS_CREATE -> create(request, session, admin, args);
            case Actions.ADMIN_OFFERS_PAUSE ->
                change(
                        request,
                        admin,
                        id,
                        "pause",
                        List.of(OfferState.SCHEDULED, OfferState.ACTIVE),
                        OfferState.PAUSED);
            case Actions.ADMIN_OFFERS_RESUME ->
                change(request, admin, id, "resume", List.of(OfferState.PAUSED), OfferState.ACTIVE);
            case Actions.ADMIN_OFFERS_CANCEL ->
                change(request, admin, id, "cancel", List.copyOf(OfferState.RUNNING), OfferState.CANCELLED);
            case Actions.ADMIN_OFFERS_OPEN ->
                open(request, admin, id, args.path("count").asInt(0));
            case Actions.ADMIN_OFFERS_PRIZE -> prize(request, admin, id, args);
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private List<OfferRow> list() {
        return sources.offers().offers(LISTED).stream()
                .map(offer -> new OfferRow(
                        offer.id(),
                        EventWrites.lower(offer.kind()),
                        offer.name(),
                        EventWrites.item(offer.line()),
                        EventWrites.lower(offer.state()),
                        offer.delivered(),
                        offer.amount(),
                        offer.percent(),
                        source(offer),
                        offer.account(),
                        offer.budget(),
                        Math.max(0, offer.spent()),
                        offer.budgetLeft(),
                        instant(offer.starts()),
                        instant(offer.ends())))
                .toList();
    }

    private ActionResult info(ActionRequest request, String admin, int id) {
        var offer = id > 0 ? sources.offers().offer(id) : Optional.<EventOffer>empty();
        if (offer.isEmpty()) return unknown(request, id);
        sources.log().entry(admin, "offer info").with("id", id).write();
        return answers.data(request, details(offer.get()));
    }

    private ActionResult create(ActionRequest request, StoredSession session, String admin, JsonNode args) {
        var kind = args.path("kind").asString("");
        if (!kind.equals("goal") && !kind.equals("contest")) return answers.refused(request, "dashboard.invalid");
        var goal = kind.equals("goal");
        var open = args.path("open").asInt(GoalDraft.OPEN_CHUNKS);
        EventOffer offer;
        try {
            var line = line(args);
            var name = args.path("name").asString("");
            var money = args.path("money").asString("");
            var amount = args.path("amount").asInt(0);
            var price = args.path("price").asDouble(Double.NaN);
            var start = args.path("start").asInt(0);
            var hours = args.path("hours").asInt(0);
            var budget = args.path("budget").isNumber() ? args.path("budget").asDouble() : null;
            var now = sources.now().get();
            offer = goal
                    ? GoalDraft.goal(
                            name,
                            money,
                            line,
                            amount,
                            price,
                            args.path("chunk").asInt(0),
                            open,
                            start,
                            hours,
                            budget,
                            session.playerName(),
                            now)
                    : GoalDraft.contest(
                            name,
                            money,
                            line,
                            amount,
                            price,
                            GoalDraft.prizes(prizes(args.path("prizes"))),
                            start,
                            hours,
                            budget,
                            session.playerName(),
                            now);
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
        var created = sources.offers().create(offer);
        if (created.isEmpty()) return answers.refused(request, PREFIX + "error.failed");
        var id = created.get();
        var line = offer.line();
        sources.log()
                .entry(admin, "offer " + kind + " create")
                .with("id", id)
                .with("name", offer.name())
                .with("item", line.label())
                .with("amount", offer.amount())
                .with("price", offer.unitPrice())
                .with("money", offer.account() == null ? GoalDraft.MINTED : offer.account())
                .with("budget", offer.budget())
                .with("prizes", offer.prizes().toString())
                .write();
        checkSoon();
        return detailsOf(request, id);
    }

    /**
     * What a new offer orders: {@code material} names a material, {@code criteria} holds criteria in the text form
     * players type.
     */
    private static OfferLine line(JsonNode args) {
        var criteria = args.path("criteria").asString("");
        if (!criteria.isBlank()) return GoalDraft.criteria(criteria);
        var material = args.path("material").asString("").trim();
        return GoalDraft.material(material, OffersCommand::canonical);
    }

    /**
     * @param prizes the money of the first places as numbers, or as the text admins type in game
     * @return the prizes as the command reads them
     */
    static String prizes(JsonNode prizes) {
        if (prizes.isString()) return prizes.asString();
        if (!prizes.isArray() || prizes.isEmpty()) return GoalDraft.NO_PRIZES;
        var parts = new ArrayList<String>();
        for (var prize : prizes) {
            if (!prize.isNumber())
                throw CommandFailure.of(PREFIX + "error.prizes", Values.number("max", GoalDraft.MAX_PLACES));
            parts.add(BigDecimal.valueOf(prize.asDouble()).stripTrailingZeros().toPlainString());
        }
        return String.join("+", parts);
    }

    private ActionResult change(
            ActionRequest request, String admin, int id, String action, List<OfferState> from, OfferState to) {
        if (id <= 0) return unknown(request, id);
        var result = OffersCommand.transition(sources.offers(), id, from, to);
        return switch (result) {
            case UNKNOWN -> unknown(request, id);
            case REFUSED, WRONG_KIND, EARLY ->
                answers.refused(request, CommandFailure.of(PREFIX + "error." + action, Values.number("id", id)));
            case DONE -> {
                sources.log().entry(admin, "offer " + action).with("id", id).write();
                checkSoon();
                yield detailsOf(request, id);
            }
        };
    }

    private ActionResult open(ActionRequest request, String admin, int id, int count) {
        if (id <= 0) return unknown(request, id);
        if (count < 1 || count > MAX_OPEN) return answers.refused(request, PREFIX + "error.numbers");
        var result = OffersCommand.limit(sources.offers(), id, count);
        return switch (result) {
            case UNKNOWN -> unknown(request, id);
            case REFUSED, EARLY ->
                answers.refused(request, CommandFailure.of(PREFIX + "error.open", Values.number("id", id)));
            case WRONG_KIND ->
                answers.refused(request, CommandFailure.of(PREFIX + "error.notGoal", Values.number("id", id)));
            case DONE -> {
                sources.log()
                        .entry(admin, "offer open")
                        .with("id", id)
                        .with("count", count)
                        .write();
                checkSoon();
                yield detailsOf(request, id);
            }
        };
    }

    private ActionResult prize(ActionRequest request, String admin, int id, JsonNode args) {
        var place = args.path("place").asInt(0);
        var outcome = args.path("outcome").asString("");
        if (!outcome.equals("paid") && !outcome.equals("failed")) {
            return answers.refused(request, "dashboard.invalid");
        }
        var values = new TagResolver[] {Values.number("id", id), Values.number("place", place)};
        var result = OffersCommand.resolve(sources.offers(), sources.funding(), id, place, outcome.equals("paid"));
        return switch (result) {
            case UNKNOWN, WRONG_KIND ->
                answers.refused(request, CommandFailure.of(PREFIX + "error.unknownPrize", values));
            case REFUSED, EARLY -> answers.refused(request, CommandFailure.of(PREFIX + "error.notPaying", values));
            case DONE -> {
                sources.log()
                        .entry(admin, "offer prize")
                        .with("id", id)
                        .with("place", place)
                        .with("outcome", outcome)
                        .write();
                yield detailsOf(request, id);
            }
        };
    }

    private ActionResult detailsOf(ActionRequest request, int id) {
        return sources.offers()
                .offer(id)
                .map(offer -> answers.data(request, details(offer)))
                .orElseGet(() -> unknown(request, id));
    }

    private ActionResult unknown(ActionRequest request, int id) {
        return answers.refused(request, CommandFailure.of(PREFIX + "error.unknown", Values.number("id", id)));
    }

    private OfferDetails details(EventOffer offer) {
        var data = sources.offers();
        var contest = offer.kind() == OfferKind.CONTEST;
        var waiting = 0;
        var taken = 0;
        var contributors = new ArrayList<ContributorRow>();
        var ranking = new ArrayList<EntryRow>();
        var prizes = new ArrayList<PrizeRow>();
        if (contest) {
            var place = 1;
            for (var entry : data.ranking(offer.id())) {
                ranking.add(new EntryRow(
                        place++,
                        entry.company(),
                        entry.name(),
                        entry.delivered(),
                        entry.paid(),
                        entry.active(),
                        instant(entry.joined()),
                        entry.left() == null ? null : instant(entry.left())));
            }
            for (var prize : data.prizes(offer.id())) {
                prizes.add(new PrizeRow(
                        prize.place(),
                        prize.amount(),
                        prize.company(),
                        prize.name(),
                        EventWrites.lower(prize.state())));
            }
        } else {
            for (var chunk : data.openChunks(offer.id())) {
                if (chunk.unclaimed()) waiting += chunk.amount();
                else taken += chunk.amount();
            }
            var rank = 1;
            for (var contributor : data.contributors(offer.id(), CONTRIBUTORS)) {
                var worker = contributor.soloWorker();
                contributors.add(new ContributorRow(
                        rank++,
                        worker == null ? contributor.company() : null,
                        worker == null ? contributor.name() : sources.names().apply(worker),
                        worker != null,
                        contributor.delivered(),
                        contributor.chunks()));
            }
        }
        return new OfferDetails(
                offer.id(),
                EventWrites.lower(offer.kind()),
                offer.name(),
                EventWrites.item(offer.line()),
                lineKind(offer.line()),
                EventWrites.lower(offer.state()),
                offer.amount(),
                offer.delivered(),
                offer.percent(),
                offer.unitPrice(),
                contest ? null : offer.chunkSize(),
                contest ? null : offer.openChunks(),
                waiting,
                taken,
                source(offer),
                offer.account(),
                offer.budget(),
                Math.max(0, offer.spent()),
                offer.budgetLeft(),
                contest && offer.started() != null && offer.source() == MoneySource.SourceType.ACCOUNT
                        ? offer.held()
                        : null,
                offer.prizes(),
                offer.prizeTotal(),
                offer.createdBy(),
                instant(offer.created()),
                instant(offer.starts()),
                instant(offer.ends()),
                optional(offer.started()),
                optional(offer.closed()),
                optional(offer.settled()),
                offer.refund(),
                optional(offer.refunded()),
                contributors,
                ranking,
                prizes);
    }

    private static String lineKind(OfferLine line) {
        if (line.criteria() != null) return "criteria";
        return line.item() != null ? "item" : "material";
    }

    private static String source(EventOffer offer) {
        return offer.source() == MoneySource.SourceType.ACCOUNT ? "account" : "minted";
    }

    private Instant instant(LocalDateTime time) {
        return sources.instant().apply(time);
    }

    private @Nullable Instant optional(@Nullable LocalDateTime time) {
        return time == null ? null : instant(time);
    }

    private void checkSoon() {
        try {
            sources.checkSoon().run();
        } catch (RuntimeException e) {
            sources.logger().log(Level.WARNING, "Could not ask the event offers to be checked soon.", e);
        }
    }

    /**
     * What the event offers are read and changed with.
     *
     * @param offers    the offer repository
     * @param funding   gives a failed prize back to the account that paid it
     * @param checkSoon asks the primary to place and withdraw chunks soon
     * @param names     a player's name by id, for players who delivered to a goal alone
     * @param instant   turns stored times into instants
     * @param now       the current time as stored
     * @param log       the admin log
     * @param logger    reports a check that could not be asked for
     * @param render    renders a refusal as plain text
     */
    public record Sources(
            AOfferData offers,
            SystemOrderService.Funding funding,
            Runnable checkSoon,
            Function<UUID, String> names,
            Function<LocalDateTime, Instant> instant,
            Supplier<LocalDateTime> now,
            AdminLog log,
            Logger logger,
            Function<CommandFailure, String> render) {}

    /**
     * An offer in the admin list.
     *
     * @param id        id of the offer
     * @param kind      {@code goal} or {@code contest}
     * @param name      name of the offer
     * @param item      what it orders
     * @param state     its state in lower case
     * @param delivered items delivered so far
     * @param amount    items asked for in all
     * @param percent   the share delivered, from 0 to 100
     * @param source    {@code minted} or {@code account}
     * @param account   the paying account, null for minted money
     * @param budget    what the offer may spend
     * @param spent     what it spent so far
     * @param left      what it may still spend
     * @param starts    when it starts
     * @param ends      when it ends
     */
    public record OfferRow(
            int id,
            String kind,
            String name,
            EventWrites.OfferItem item,
            String state,
            int delivered,
            int amount,
            int percent,
            String source,
            @Nullable String account,
            double budget,
            double spent,
            double left,
            Instant starts,
            Instant ends) {}

    /**
     * The newest offers.
     *
     * @param offers the offers, the newest first
     */
    public record OfferList(List<OfferRow> offers) {}

    /**
     * Who delivered to a goal, as admins see it.
     *
     * @param rank      the rank, by items delivered
     * @param company   id of the company, null for a player working alone
     * @param name      the company's or the player's name
     * @param alone     whether a player delivered without a company
     * @param delivered items delivered
     * @param chunks    chunks delivered
     */
    public record ContributorRow(
            int rank, @Nullable Integer company, @Nullable String name, boolean alone, int delivered, int chunks) {}

    /**
     * A company in a contest, as admins see it.
     *
     * @param place     its place by items delivered
     * @param company   id of the company
     * @param name      the company's name, null once it was deleted
     * @param delivered items delivered
     * @param paid      money its workers were paid
     * @param active    whether it still takes part
     * @param joined    when it joined
     * @param left      when it left, null while it takes part
     */
    public record EntryRow(
            int place,
            int company,
            @Nullable String name,
            int delivered,
            double paid,
            boolean active,
            Instant joined,
            @Nullable Instant left) {}

    /**
     * A settled prize of a contest.
     *
     * @param place   the place it is for
     * @param amount  its money
     * @param company id of the winning company, null when no company won it
     * @param name    the winner's name
     * @param state   {@code pending}, {@code paying}, {@code paid}, {@code failed} or {@code none}
     */
    public record PrizeRow(
            int place,
            double amount,
            @Nullable Integer company,
            @Nullable String name,
            String state) {}

    /**
     * An offer with what its decision record shows.
     *
     * @param id           id of the offer
     * @param kind         {@code goal} or {@code contest}
     * @param name         name of the offer
     * @param item         what it orders
     * @param line         {@code material}, {@code criteria} or {@code item}
     * @param state        its state in lower case
     * @param amount       items asked for in all
     * @param delivered    items delivered so far
     * @param percent      the share delivered, from 0 to 100
     * @param unitPrice    price per item
     * @param chunkSize    items per chunk of a goal, null for a contest
     * @param openChunks   chunks of a goal waiting at once, null for a contest
     * @param waiting      items of chunks waiting to be taken
     * @param taken        items of chunks taken and not delivered yet
     * @param source       {@code minted} or {@code account}
     * @param account      the paying account, null for minted money
     * @param budget       what the offer may spend
     * @param spent        what it spent so far
     * @param left         what it may still spend
     * @param held         what a started contest took from its account, null otherwise
     * @param prizeMoney   the money of the first places of a contest
     * @param prizeTotal   their sum
     * @param createdBy    who created it
     * @param created      when it was created
     * @param starts       when it starts
     * @param ends         when it ends
     * @param started      when it first started, null before
     * @param closed       when it closed, null while it runs
     * @param settled      when a contest settled its prizes, null before
     * @param refund       what a contest gave back, null before
     * @param refunded     when a contest gave back its rest, null before
     * @param contributors the top contributors of a goal
     * @param ranking      the companies of a contest by place
     * @param prizes       the settled prizes of a contest
     */
    public record OfferDetails(
            int id,
            String kind,
            String name,
            EventWrites.OfferItem item,
            String line,
            String state,
            int amount,
            int delivered,
            int percent,
            double unitPrice,
            @Nullable Integer chunkSize,
            @Nullable Integer openChunks,
            int waiting,
            int taken,
            String source,
            @Nullable String account,
            double budget,
            double spent,
            double left,
            @Nullable Double held,
            List<Double> prizeMoney,
            double prizeTotal,
            String createdBy,
            Instant created,
            Instant starts,
            Instant ends,
            @Nullable Instant started,
            @Nullable Instant closed,
            @Nullable Instant settled,
            @Nullable Double refund,
            @Nullable Instant refunded,
            List<ContributorRow> contributors,
            List<EntryRow> ranking,
            List<PrizeRow> prizes) {}
}
