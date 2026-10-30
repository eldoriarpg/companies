/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.events.EventTemplate;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Creates the events of the templates of {@code events.yml}, on the primary, as part of the checks of
 * {@link ServerEventService}.
 * <p>
 * A template creates one event at a time: while one of its events waits or runs, it creates nothing. Its next event is
 * the first start, weekly or at one of its dates, at or after the planned end of the event the template created last,
 * whatever came of it, and whose event would not have ended yet; it is created once it is at most
 * {@code createAheadHours} away. So a template never creates the same start twice and never two events whose times
 * overlap, also when the primary restarts, an admin cancelled or ended the event, or the template's start or the
 * server's time zone changed meanwhile; and a start whose whole time passed while the primary was down is skipped. A
 * start an admin cancelled stays skipped; an event the primary withdrew since its template was disabled or removed does
 * not count, so a template enabled again before that event's end creates the start again. A duration longer than the
 * time to the next start skips the starts the event still covers, and a date inside the time of the date before it is
 * logged once. An
 * event whose start passed already, since the primary came up while it should run, is created with its planned start
 * and end and starts at once.
 * <p>
 * The id is the identity of a template: its events remember it, and a change of its start, duration or zone applies
 * from the next start after the last event. A renamed template, a new id, has no last event, so it may create an
 * event at once even while one of its old id runs. A change of the value, the scope or the money does not reach an
 * event already created; cancel that event and create a replacement by command, or change its budget by command. An
 * event that waits for its start while its template is disabled or removed is withdrawn, see {@link #keeps}; one that
 * runs keeps running. While {@code events.yml} cannot be read, nothing is created and nothing withdrawn.
 * <p>
 * Weekly starts and dates are times of the server's time zone: an event every {@code SATURDAY 18:00} starts at 18:00
 * on the server's clock in summer and winter alike; a start the clocks skip moves by the gap, and a start that exists
 * twice is the earlier one. The duration is real time from the start. A template with a problem, such as a weekly start
 * that cannot be read, creates nothing; each problem is logged once until it changes.
 */
public final class EventSchedule implements ServerEventService.Schedule {
    /**
     * Who the events of templates name as their creator.
     */
    public static final String CREATOR = "events.yml";

    private final Supplier<EventSettings> settings;
    private final AServerEventData data;
    private final Supplier<ZoneId> zone;
    private final Supplier<String> collectorAccount;
    private final Logger logger;
    private final Set<String> logged = ConcurrentHashMap.newKeySet();
    private final Set<String> refusals = ConcurrentHashMap.newKeySet();
    private final Set<String> refusedNow = ConcurrentHashMap.newKeySet();
    private final BooleanSupplier readable;

    /**
     * @param settings         the current {@code events.yml}
     * @param data             event repository, holding the events the templates created
     * @param zone             the server's time zone
     * @param collectorAccount the account of {@code economy.collectorAccount}, paying templates funded from an account
     *                         that name none
     * @param logger           reports templates with problems
     */
    public EventSchedule(
            Supplier<EventSettings> settings,
            AServerEventData data,
            Supplier<ZoneId> zone,
            Supplier<String> collectorAccount,
            Logger logger) {
        this(settings, data, zone, collectorAccount, () -> true, logger);
    }

    /**
     * @param settings         the current {@code events.yml}
     * @param data             event repository, holding the events the templates created
     * @param zone             the server's time zone
     * @param collectorAccount the account of {@code economy.collectorAccount}, paying templates funded from an account
     *                         that name none
     * @param readable         whether {@code events.yml} could be read, so its templates are the admin's and not the
     *                         defaults
     * @param logger           reports templates with problems
     */
    public EventSchedule(
            Supplier<EventSettings> settings,
            AServerEventData data,
            Supplier<ZoneId> zone,
            Supplier<String> collectorAccount,
            BooleanSupplier readable,
            Logger logger) {
        this.readable = readable;
        this.settings = settings;
        this.data = data;
        this.zone = zone;
        this.collectorAccount = collectorAccount;
        this.logger = logger;
    }

    @Override
    public List<ServerEvent> due(LocalDateTime now) {
        refusals.retainAll(refusedNow);
        refusedNow.clear();
        if (!readable.getAsBoolean()) return List.of();
        var current = settings.get();
        var ahead = Duration.ofHours(current.createAheadHours());
        var zoneId = zone.get();
        Set<String> seen = new HashSet<>();
        Set<String> problems = new HashSet<>();
        List<ServerEvent> due = new ArrayList<>();
        for (var template : current.templates()) {
            var id = template.id();
            if (id.isEmpty() || id.length() > EventTemplate.MAX_ID_LENGTH || !seen.add(id)) {
                problem(problems, id, "has no id, an id longer than 64 characters, or the id of an earlier template");
                continue;
            }
            if (!template.enabled()) continue;
            next(template, now, ahead, zoneId, problems).ifPresent(due::add);
        }
        logged.retainAll(problems);
        return due;
    }

    @Override
    public void refused(ServerEvent draft, ServerEvent.Problem problem) {
        var template = draft.template();
        if (template == null) return;
        var key = template + ": " + problem;
        refusedNow.add(key);
        if (refusals.add(key)) {
            logger.warning("The template " + template + " of events.yml creates no event: " + problem
                    + ". Fix it and reload.");
        }
    }

    /**
     * Whether an event of a template that waits for its start is kept: only while its template is in
     * {@code events.yml} and enabled. Events created by hand are always kept, and so is everything while
     * {@code events.yml} cannot be read.
     *
     * @param event an event waiting for its start
     * @return false when it is to be withdrawn
     */
    @Override
    public boolean keeps(ServerEvent event) {
        var template = event.template();
        if (template == null || !readable.getAsBoolean()) return true;
        return settings.get().templates().stream()
                .anyMatch(found -> found.enabled() && found.id().equals(template));
    }

    private Optional<ServerEvent> next(
            EventTemplate template, LocalDateTime now, Duration ahead, ZoneId zoneId, Set<String> problems) {
        var id = template.id();
        var duration = EventTimes.duration(template.duration());
        if (duration.isEmpty()) {
            problem(problems, id, "its duration \"" + template.duration() + "\" is no duration such as 48h");
            return Optional.empty();
        }
        var weekly = Optional.<EventTimes.Weekly>empty();
        if (!template.every().isEmpty()) {
            weekly = EventTimes.weekly(template.every());
            if (weekly.isEmpty()) {
                problem(
                        problems,
                        id,
                        "its start \"" + template.every() + "\" is no weekly start such as SATURDAY 18:00");
                return Optional.empty();
            }
        }
        List<LocalDateTime> dates = new ArrayList<>();
        for (var date : template.dates()) {
            var parsed = EventTimes.date(date, zoneId);
            if (parsed.isEmpty()) {
                problem(problems, id, "its date \"" + date + "\" is no date such as 2026-12-24 18:00");
                return Optional.empty();
            }
            dates.add(parsed.get());
        }
        var sorted = dates.stream().sorted().toList();
        for (var index = 1; index < sorted.size(); index++) {
            if (sorted.get(index).isBefore(sorted.get(index - 1).plus(duration.get()))) {
                problem(
                        problems,
                        id,
                        "its date " + EventTimes.show(sorted.get(index), zoneId)
                                + " lies within the event of the date before it and is skipped");
            }
        }
        if (weekly.isEmpty() && dates.isEmpty()) {
            problem(problems, id, "it has neither a weekly start nor dates");
            return Optional.empty();
        }
        var latest = data.latest(id);
        if (latest.isPresent() && !latest.get().state().closed()) return Optional.empty();
        var from = from(now, duration.get(), latest);
        var start = earliest(weekly.map(found -> found.next(from, zoneId)), dates, from);
        if (start == null || start.minus(ahead).isAfter(now)) return Optional.empty();
        return Optional.of(draft(template, start, start.plus(duration.get()), now));
    }

    private static LocalDateTime from(LocalDateTime now, Duration duration, Optional<ServerEvent> latest) {
        var from = now.minus(duration).plusSeconds(1);
        if (latest.isPresent() && latest.get().ends().isAfter(from))
            return latest.get().ends();
        return from;
    }

    private static @Nullable LocalDateTime earliest(
            Optional<LocalDateTime> weekly, List<LocalDateTime> dates, LocalDateTime from) {
        LocalDateTime earliest = weekly.orElse(null);
        for (var date : dates) {
            if (date.isBefore(from)) continue;
            if (earliest == null || date.isBefore(earliest)) earliest = date;
        }
        return earliest;
    }

    private ServerEvent draft(EventTemplate template, LocalDateTime starts, LocalDateTime ends, LocalDateTime now) {
        var kind = template.kind();
        var funding = kind.funded() ? template.funding() : null;
        String account = null;
        if (funding == MoneySource.SourceType.ACCOUNT) {
            account = template.account().isEmpty() ? collectorAccount.get() : template.account();
        }
        var budget = kind.funded() && template.budget() > 0 ? template.budget() : null;
        return ServerEvent.draft(
                template.name(),
                kind,
                template.value(),
                template.materials(),
                template.levels(),
                starts,
                ends,
                funding,
                account,
                budget,
                template.id(),
                CREATOR,
                now);
    }

    private void problem(Set<String> problems, String id, String problem) {
        var key = id + ": " + problem;
        problems.add(key);
        if (logged.add(key)) {
            logger.warning("The template " + (id.isEmpty() ? "without id" : id) + " of events.yml has a problem: "
                    + problem + ". Fix it and reload.");
        }
    }
}
