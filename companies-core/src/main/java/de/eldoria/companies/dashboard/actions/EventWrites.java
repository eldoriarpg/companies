/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.company.EventsCommand;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.dashboard.views.PlainText;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.offers.ContestService;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The dashboard's Events page, as {@code /company events} and {@code /company contest} offer it: the event offers a
 * player sees, a goal with its top contributors, a contest with its ranking, prizes and the place of the player's
 * company, and joining or leaving a contest for the company. An offer that started once can still be opened after it
 * closed, with its final ranking and prize winners; a contest cancelled without settling shows no prizes. Joining and
 * leaving need the permission to manage the company's orders and go through {@link ContestService} as the commands
 * do; delivering into a contest stays in game, as it takes the items from the player's inventory. Solo workers who
 * delivered to a goal are not named. The methods block on the database and are called off the main thread.
 */
public final class EventWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS =
            Set.of(Actions.EVENTS_LIST, Actions.EVENTS_INFO, Actions.EVENTS_JOIN, Actions.EVENTS_LEAVE);

    /**
     * The most contributors of a goal shown.
     */
    static final int CONTRIBUTORS = 10;

    private static final String PREFIX = "company.contest.";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the offers are read and joined with
     */
    public EventWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an action of the Events page.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the player's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        var player = session.player();
        var company = sources.companyOf().apply(player).join();
        return switch (request.name()) {
            case Actions.EVENTS_LIST -> answers.data(request, list(company, player));
            case Actions.EVENTS_INFO ->
                info(request, company, player, args.path("offer").asInt(0));
            case Actions.EVENTS_JOIN ->
                joining(request, company, player, args.path("offer").asInt(0), true);
            case Actions.EVENTS_LEAVE ->
                joining(request, company, player, args.path("offer").asInt(0), false);
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private EventList list(Optional<CompanyProfile> company, UUID player) {
        var data = sources.offers();
        var shown = EventsCommand.shown(data.running(), sources.now().get());
        Set<Integer> joined = company.map(found -> (Set<Integer>) new HashSet<>(data.joined(found.id())))
                .orElse(Set.of());
        var rows = shown.stream()
                .map(offer -> row(
                        offer,
                        offer.kind() == OfferKind.CONTEST && company.isPresent()
                                ? place(data.ranking(offer.id()), company.get().id())
                                : null,
                        joined.contains(offer.id())))
                .toList();
        var events = EventsCommand.shownEvents(
                        sources.events().live(), sources.now().get())
                .stream()
                .map(this::serverEvent)
                .toList();
        return new EventList(rows, events, company.isPresent(), canManage(company, player));
    }

    private ActionResult info(ActionRequest request, Optional<CompanyProfile> company, UUID player, int id) {
        var offer = visible(id);
        if (offer.isEmpty()) return answers.refused(request, "dashboard.offerUnknown");
        return answers.data(request, details(offer.get(), company, player));
    }

    /**
     * An offer a player may open: one the list shows, and any other that started once, so a closed contest keeps its
     * ranking and prizes and a chunk delivered late still leads to its goal. An offer scheduled beyond the list's day
     * and one cancelled before it started stay unknown.
     *
     * @param id id of an offer
     * @return the offer when a player may open it
     */
    private Optional<EventOffer> visible(int id) {
        if (id <= 0) return Optional.empty();
        var found = sources.offers().offer(id);
        if (found.isEmpty()) return Optional.empty();
        if (found.get().started() != null) return found;
        return EventsCommand.shown(List.of(found.get()), sources.now().get()).stream()
                .filter(offer -> !offer.state().closed())
                .findFirst();
    }

    private EventDetails details(EventOffer offer, Optional<CompanyProfile> company, UUID player) {
        var data = sources.offers();
        var manage = canManage(company, player);
        if (offer.kind() == OfferKind.GOAL) {
            var contributors = new ArrayList<ContributorView>();
            var rank = 1;
            for (var contributor : data.contributors(offer.id(), CONTRIBUTORS)) {
                var alone = contributor.soloWorker() != null;
                contributors.add(new ContributorView(
                        rank++,
                        alone ? null : contributor.name(),
                        alone,
                        contributor.delivered(),
                        contributor.chunks()));
            }
            return new EventDetails(
                    row(offer, null, false),
                    offer.chunkSize(),
                    contributors,
                    List.of(),
                    List.of(),
                    null,
                    company.isPresent(),
                    manage);
        }
        var ranking = data.ranking(offer.id());
        var places = new ArrayList<RankView>();
        var place = 0;
        var own = company.map(CompanyProfile::id).orElse(0);
        for (var entry : ranking) {
            if (entry.delivered() <= 0 || entry.name() == null) continue;
            place++;
            places.add(new RankView(place, entry.name(), entry.delivered(), entry.active(), entry.company() == own));
        }
        var prizes = new ArrayList<PrizeView>();
        var settled = offer.state().closed() ? data.prizes(offer.id()) : List.<ContestPrize>of();
        if (!settled.isEmpty()) {
            for (var prize : settled) {
                prizes.add(new PrizeView(prize.place(), prize.amount(), prize.name(), lower(prize.state())));
            }
        } else if (offer.state() != OfferState.CANCELLED) {
            for (var index = 0; index < offer.prizes().size(); index++) {
                prizes.add(new PrizeView(index + 1, offer.prizes().get(index), null, "pending"));
            }
        }
        OwnPart part = null;
        if (company.isPresent()) {
            var entry = data.entry(offer.id(), own);
            var ownPlace = place(ranking, own);
            part = new OwnPart(
                    company.get().name(),
                    entry.map(ContestEntry::active).orElse(false),
                    entry.map(ContestEntry::delivered).orElse(0),
                    entry.map(ContestEntry::paid).orElse(0.0),
                    ownPlace);
        }
        var joined = part != null && part.joined();
        return new EventDetails(
                row(offer, part == null ? null : part.place(), joined),
                null,
                List.of(),
                places,
                prizes,
                part,
                company.isPresent(),
                manage);
    }

    private ActionResult joining(
            ActionRequest request, Optional<CompanyProfile> company, UUID player, int id, boolean join) {
        if (company.isEmpty()) return answers.refused(request, "error.noMember");
        if (id <= 0) return answers.refused(request, PREFIX + "error.unknown");
        var contests = sources.contests();
        var result = join ? contests.join(company.get(), player, id) : contests.leave(company.get(), player, id);
        var number = Values.number("id", id);
        return switch (result) {
            case JOINED, LEFT -> {
                var offer = sources.offers().offer(id);
                if (offer.isEmpty()) yield answers.refused(request, PREFIX + "error.unknown");
                var refreshed = sources.companyOf().apply(player).join();
                yield answers.data(request, details(offer.get(), refreshed, player));
            }
            case ALREADY -> answers.refused(request, CommandFailure.of(PREFIX + "error.already", number));
            case NOT_JOINED -> answers.refused(request, CommandFailure.of(PREFIX + "error.notJoined", number));
            case NO_PERMISSION -> answers.refused(request, PREFIX + "error.permission");
            case UNKNOWN -> answers.refused(request, PREFIX + "error.unknown");
            case CLOSED -> answers.refused(request, CommandFailure.of(PREFIX + "error.closed", number));
            case LIMIT -> answers.refused(request, "error.orderLimit");
            case NOT_STARTED -> answers.refused(request, notStarted(id, number));
        };
    }

    private CommandFailure notStarted(int id, TagResolver number) {
        var left = sources.offers()
                .offer(id)
                .map(offer -> Duration.between(sources.now().get(), offer.starts()))
                .map(duration -> Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration))
                .orElse("-");
        return CommandFailure.of(PREFIX + "error.notStarted", number, Values.text("time", left));
    }

    private EventRow row(EventOffer offer, @Nullable Integer place, boolean joined) {
        return new EventRow(
                offer.id(),
                lower(offer.kind()),
                offer.name(),
                item(offer.line()),
                offer.amount(),
                offer.delivered(),
                offer.percent(),
                offer.unitPrice(),
                lower(offer.state()),
                sources.instant().apply(offer.starts()),
                sources.instant().apply(offer.ends()),
                offer.prizeTotal(),
                place,
                joined,
                offer.event());
    }

    private ServerEventView serverEvent(ServerEvent event) {
        var left = event.remaining();
        return new ServerEventView(
                event.id(),
                lower(event.kind()),
                event.name(),
                event.value(),
                event.materials(),
                event.levels(),
                lower(event.state()),
                sources.instant().apply(event.starts()),
                sources.instant().apply(event.ends()),
                left.isPresent() ? left.getAsDouble() : null);
    }

    private static boolean canManage(Optional<CompanyProfile> company, UUID player) {
        return company.flatMap(found -> found.member(player))
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private static @Nullable Integer place(List<ContestEntry> ranking, int company) {
        var place = EventsCommand.place(ranking, company);
        return place.isPresent() ? place.getAsInt() : null;
    }

    /**
     * Describes what an offer orders.
     *
     * @param line the offer's line
     * @return the item as the dashboard shows it
     */
    static OfferItem item(OfferLine line) {
        var criteria = line.criteria();
        if (criteria != null) return new OfferItem(line.label(), null, criteria);
        var stack = line.item();
        if (stack != null) {
            var view = Views.item(stack, 1);
            return new OfferItem(view.name(), view.icon(), null);
        }
        var name = line.material();
        var material = name == null ? null : Material.matchMaterial(name);
        if (material == null) return new OfferItem(line.label(), null, null);
        return new OfferItem(PlainText.materialName(material), material.getKey().getKey(), null);
    }

    /**
     * @param value a constant
     * @return its name in lower case, as the dashboard names states and kinds
     */
    static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * What the Events page is read and joined with.
     *
     * @param offers    the offer repository
     * @param events    the server event repository
     * @param contests  joins and leaves contests
     * @param companyOf the company of a player
     * @param instant   turns stored times into instants
     * @param now       the current time as stored
     * @param render    renders a refusal as plain text
     */
    public record Sources(
            AOfferData offers,
            AServerEventData events,
            ContestService contests,
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            Function<LocalDateTime, Instant> instant,
            Supplier<LocalDateTime> now,
            Function<CommandFailure, String> render) {}

    /**
     * What an offer orders.
     *
     * @param name     the item's name, or the criteria as players type them
     * @param icon     the icon of a material, null for criteria
     * @param criteria the criteria of a criteria line, null otherwise
     */
    public record OfferItem(
            String name, @Nullable String icon, @Nullable String criteria) {}

    /**
     * An offer in the list of the Events page.
     *
     * @param id         id of the offer
     * @param kind       {@code goal} or {@code contest}
     * @param name       name of the offer
     * @param item       what it orders
     * @param amount     items asked for in all
     * @param delivered  items delivered so far
     * @param percent    the share delivered, from 0 to 100
     * @param unitPrice  price per item
     * @param state      {@code scheduled}, {@code active} or {@code paused} in the list; an opened offer may also be
     *                   closed: {@code reached}, {@code ended}, {@code depleted} or {@code cancelled}
     * @param starts     when it starts
     * @param ends       when it ends
     * @param prizeTotal the money of the prizes of a contest, 0 for a goal
     * @param place      the place of the player's company in a contest, null without one
     * @param joined     whether the player's company takes part in the contest
     * @param event      the server event the offer belongs to, null for one that stands alone
     */
    public record EventRow(
            int id,
            String kind,
            String name,
            OfferItem item,
            int amount,
            int delivered,
            int percent,
            double unitPrice,
            String state,
            Instant starts,
            Instant ends,
            double prizeTotal,
            @Nullable Integer place,
            boolean joined,
            @Nullable Integer event) {}

    /**
     * A server event on the Events page: running, or starting within a day.
     *
     * @param id         id of the event
     * @param kind       {@code tax_reduction}, {@code reward_multiplier} or {@code fee_reduction}
     * @param name       name of the event
     * @param value      how much it changes: percentage points off the tax, the factor on payouts, percent off fees
     * @param materials  the materials it is limited to, empty for every material
     * @param levels     the company levels it is limited to, empty for every company
     * @param state      {@code scheduled} or {@code active}
     * @param starts     when it starts
     * @param ends       when it ends
     * @param budgetLeft what a multiplier with a budget can still pay, null otherwise
     */
    public record ServerEventView(
            int id,
            String kind,
            String name,
            double value,
            List<String> materials,
            List<Integer> levels,
            String state,
            Instant starts,
            Instant ends,
            @Nullable Double budgetLeft) {}

    /**
     * The offers and server events of the Events page.
     *
     * @param offers       the offers, the soonest end first
     * @param serverEvents the server events running or starting within a day, the soonest start first
     * @param member       whether the player is part of a company
     * @param canManage    whether the player may let the company join or leave contests
     */
    public record EventList(
            List<EventRow> offers, List<ServerEventView> serverEvents, boolean member, boolean canManage) {}

    /**
     * Who delivered to a goal.
     *
     * @param rank      the rank, by items delivered
     * @param name      the company's name, null for a player working alone
     * @param alone     whether a player delivered without a company
     * @param delivered items delivered
     * @param chunks    chunks delivered
     */
    public record ContributorView(int rank, @Nullable String name, boolean alone, int delivered, int chunks) {}

    /**
     * A company in the ranking of a contest.
     *
     * @param place     the place among the companies that delivered
     * @param name      the company's name
     * @param delivered items delivered
     * @param active    whether it still takes part
     * @param own       whether it is the player's company
     */
    public record RankView(int place, String name, int delivered, boolean active, boolean own) {}

    /**
     * A prize of a contest.
     *
     * @param place  the place it is for
     * @param amount its money
     * @param winner the company that won it once the contest closed, null before or when no company did
     * @param state  {@code pending}, {@code paying}, {@code paid}, {@code failed} or {@code none}
     */
    public record PrizeView(
            int place, double amount, @Nullable String winner, String state) {}

    /**
     * The part of the player's company in a contest.
     *
     * @param company   the company's name
     * @param joined    whether it takes part
     * @param delivered items it delivered
     * @param paid      money its workers were paid
     * @param place     its place, null before it delivered
     */
    public record OwnPart(
            String company,
            boolean joined,
            int delivered,
            double paid,
            @Nullable Integer place) {}

    /**
     * An offer as its page on the dashboard shows it.
     *
     * @param offer        the offer
     * @param chunkSize    items per chunk of a goal, null for a contest
     * @param contributors the top contributors of a goal
     * @param ranking      the companies of a contest that delivered, by place
     * @param prizes       the prizes of a contest, with their winners once it closed
     * @param own          the part of the player's company in a contest, null without a company or for a goal
     * @param member       whether the player is part of a company
     * @param canManage    whether the player may let the company join or leave contests
     */
    public record EventDetails(
            EventRow offer,
            @Nullable Integer chunkSize,
            List<ContributorView> contributors,
            List<RankView> ranking,
            List<PrizeView> prizes,
            @Nullable OwnPart own,
            boolean member,
            boolean canManage) {}
}
