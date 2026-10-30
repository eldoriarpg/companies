/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.companyadmin.EventCommand;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.admin.EventForm;
import de.eldoria.companies.services.events.EventTimes;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The dashboard's view of the server events, as {@code /companyadmin event} offers it: the newest events with their
 * effect, scope, times, money and offers, and creating, ending, cancelling, extending an event and changing its budget.
 * A new event is read by the same {@link EventForm} as the command's form and every change goes through the
 * {@link ServerEventService} and {@link EventCommand#changed}, so both refuse the same things with the same messages.
 * The admin needs {@code companies.admin.events}. Every change is written to the admin log under the admin's name with
 * {@code @dashboard}. The methods block and are called off the main thread.
 */
public final class AdminEventWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_EVENTS_LIST,
            Actions.ADMIN_EVENTS_CREATE,
            Actions.ADMIN_EVENTS_END,
            Actions.ADMIN_EVENTS_CANCEL,
            Actions.ADMIN_EVENTS_EXTEND,
            Actions.ADMIN_EVENTS_BUDGET);

    /**
     * The most events listed, the newest first.
     */
    static final int LISTED = 50;

    /**
     * The most characters of a sent duration written to the admin log.
     */
    static final int LOGGED = 32;

    private static final String PREFIX = "companyadmin.event.";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the events are read and changed with
     */
    public AdminEventWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an admin action on server events.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.EVENTS)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        var id = args.path("event").asInt(0);
        var service = sources.service();
        return switch (request.name()) {
            case Actions.ADMIN_EVENTS_LIST ->
                answers.data(
                        request,
                        new EventList(sources.events().events(LISTED).stream()
                                .map(this::row)
                                .toList()));
            case Actions.ADMIN_EVENTS_CREATE -> create(request, admin, args);
            case Actions.ADMIN_EVENTS_END -> change(request, admin, id, "end", service.end(id, admin), Map.of());
            case Actions.ADMIN_EVENTS_CANCEL ->
                change(request, admin, id, "cancel", service.cancel(id, admin), Map.of());
            case Actions.ADMIN_EVENTS_EXTEND ->
                extend(request, admin, id, args.path("duration").asString(""));
            case Actions.ADMIN_EVENTS_BUDGET -> budget(request, admin, id, args.path("budget"));
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private ActionResult create(ActionRequest request, String admin, JsonNode args) {
        var form = EventForm.of(args.path("name").asString(""));
        for (var field : EventForm.Field.values()) {
            var text = args.path(field.key()).asString("");
            if (!text.isBlank()) form = form.with(field, text);
        }
        var saved = EventCommand.save(
                sources.service(),
                sources.log(),
                admin,
                form,
                sources.zone().get(),
                sources.now().get(),
                sources.collector().get());
        return switch (saved) {
            case EventCommand.Saved.Created created -> answers.data(request, row(created.event()));
            case EventCommand.Saved.Refused refused -> answers.refused(request, refused.key());
        };
    }

    private ActionResult extend(ActionRequest request, String admin, int id, String text) {
        var duration = EventTimes.duration(text);
        if (duration.isEmpty()) {
            return answers.refused(
                    request, CommandFailure.of(PREFIX + "error.duration", Values.text("duration", text.strip())));
        }
        return change(
                request,
                admin,
                id,
                "extend",
                sources.service().extend(id, duration.get()),
                Map.of("duration", logged(text)));
    }

    /**
     * @param text a duration as an admin sent it
     * @return the text, cut to {@link #LOGGED} characters for the admin log
     */
    static String logged(String text) {
        var stripped = text.strip();
        return stripped.length() > LOGGED ? stripped.substring(0, LOGGED) : stripped;
    }

    private ActionResult budget(ActionRequest request, String admin, int id, JsonNode value) {
        Double budget = null;
        if (!value.isNull() && !value.isMissingNode()) {
            if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() <= 0) {
                return answers.refused(request, PREFIX + "error.budget");
            }
            budget = value.asDouble();
        }
        return change(
                request,
                admin,
                id,
                "budget",
                sources.service().budget(id, budget),
                Map.of("budget", budget == null ? "none" : String.valueOf(budget)));
    }

    private ActionResult change(
            ActionRequest request,
            String admin,
            int id,
            String action,
            ServerEventService.Change result,
            Map<String, String> details) {
        var refusal = new AtomicReference<CommandFailure>();
        EventCommand.changed(
                sources.log(),
                new EventCommand.Replies() {
                    @Override
                    public void message(String key, TagResolver.Single... values) {}

                    @Override
                    public void error(String key, TagResolver.Single... values) {
                        refusal.set(CommandFailure.of(key, values));
                    }
                },
                admin,
                id,
                action,
                result,
                details);
        var failure = refusal.get();
        if (failure != null) return answers.refused(request, failure);
        return sources.events()
                .event(id)
                .map(event -> answers.data(request, row(event)))
                .orElseGet(() ->
                        answers.refused(request, CommandFailure.of(PREFIX + "error.unknown", Values.number("id", id))));
    }

    private EventRow row(ServerEvent event) {
        var offers = sources.offers().ofEvent(event.id()).stream()
                .map(offer -> new EventOfferRef(offer.id(), offer.name()))
                .toList();
        var instant = sources.instant();
        var funding = event.funding();
        var closed = event.closed();
        return new EventRow(
                event.id(),
                lower(event.kind()),
                event.name(),
                event.value(),
                event.materials(),
                event.levels(),
                lower(event.state()),
                instant.apply(event.starts()),
                instant.apply(event.ends()),
                funding == null ? null : lower(funding),
                event.account(),
                event.budget(),
                event.spent(),
                event.template(),
                event.createdBy(),
                instant.apply(event.created()),
                closed == null ? null : instant.apply(closed),
                event.closedBy(),
                offers);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * What the server events are read and changed with.
     *
     * @param events    the event repository
     * @param offers    the offer repository, for the offers of an event
     * @param service   creates and changes events as the command does
     * @param zone      the server's time zone, for the dates of the form
     * @param collector the collector account an event paid from an account uses when none is named
     * @param instant   turns stored times into instants
     * @param now       the current time as stored
     * @param log       the admin log
     * @param render    renders a refusal as plain text
     */
    public record Sources(
            AServerEventData events,
            AOfferData offers,
            ServerEventService service,
            Supplier<ZoneId> zone,
            Supplier<String> collector,
            Function<LocalDateTime, Instant> instant,
            Supplier<LocalDateTime> now,
            AdminLog log,
            Function<CommandFailure, String> render) {}

    /**
     * An event offer of a server event.
     *
     * @param id   id of the offer
     * @param name name of the offer
     */
    public record EventOfferRef(int id, String name) {}

    /**
     * A server event in the admin list.
     *
     * @param id        id of the event
     * @param kind      {@code tax_reduction}, {@code reward_multiplier} or {@code fee_reduction}
     * @param name      name shown to players
     * @param value     how much it changes
     * @param materials the materials it is limited to, empty for every material
     * @param levels    the company levels it is limited to, empty for every company
     * @param state     {@code scheduled}, {@code active}, {@code ended}, {@code depleted} or {@code cancelled}
     * @param starts    when it starts
     * @param ends      when it ends
     * @param funding   {@code account} or {@code minted} for a multiplier, null for the other kinds
     * @param account   the account paying a multiplier, null otherwise
     * @param budget    what a multiplier may pay in all, null for no cap
     * @param spent     what it paid so far
     * @param template  the template of {@code events.yml} that created it, null for one created by hand
     * @param createdBy who created it
     * @param created   when it was created
     * @param closed    when it reached a final state, null while it runs
     * @param closedBy  who ended or cancelled it; {@code #missed} or {@code #template} for the primary's markers
     * @param offers    the event offers that belong to it
     */
    public record EventRow(
            int id,
            String kind,
            String name,
            double value,
            List<String> materials,
            List<Integer> levels,
            String state,
            Instant starts,
            Instant ends,
            @Nullable String funding,
            @Nullable String account,
            @Nullable Double budget,
            double spent,
            @Nullable String template,
            String createdBy,
            Instant created,
            @Nullable Instant closed,
            @Nullable String closedBy,
            List<EventOfferRef> offers) {}

    /**
     * The newest server events.
     *
     * @param events the events, newest first
     */
    public record EventList(List<EventRow> events) {}
}
