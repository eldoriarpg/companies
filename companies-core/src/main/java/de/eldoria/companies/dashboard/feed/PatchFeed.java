/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.feed;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.gateway.GatewayLink;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.snapshot.RankingEntry;
import de.eldoria.companies.web.api.snapshot.RankingPage;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.PatchOp;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the snapshots of subscribed sessions current by pushing patches built from the plugin's events.
 * <p>
 * Each session sees only what its player may see: the owner of an order gets its {@code orders} entry without
 * company or worker, the members of the company working on it get its {@code company.orders} entry without the owner.
 * A delivery also updates the delivering member in {@code company.members}, whose items grow with the order's line;
 * inside the company the member is named, so the dashboard can show who delivered what. A value is built per viewer,
 * so the treasury stays with members holding the treasury permission. Who gets a company's patches is decided by the
 * company's current members, read with each change, so a membership that changed on another node never leaks a
 * company's data to a former member. Patches are numbered by this node's sequence;
 * the gateway asks for a fresh snapshot when it sees a gap.
 * <p>
 * Paths and keys: {@code orders} and {@code company.orders} by order id, and {@code company.members} by member uuid are
 * lists whose entries are upserted and removed. {@code player}, {@code company}, {@code player.invites} and
 * {@code rankings} are replaced as a whole per viewer ({@code company} is removed when the player
 * has none); {@code player.unreadInbox} and {@code company.treasury} are replaced.
 * <p>
 * A session is only sent patches below {@code company} once it was sent the whole {@code company} of the same id. The
 * gateway only refuses such patches for a snapshot without a company, so this ordering and the membership read with
 * each change are what keep a session to its own company. Only the company patches correct the company a subscription
 * remembers.
 * <p>
 * Patches are built one after another on the given executor, so a later change never overtakes an earlier one.
 */
public final class PatchFeed {
    private final Views views;
    private final GatewayLink link;
    private final UUID node;
    private final Executor executor;
    private final Logger logger;
    private final JsonMapper mapper = Protocol.mapper();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final AtomicBoolean rankingsDirty = new AtomicBoolean();

    /**
     * @param views    builds the views
     * @param link     sends the patches
     * @param node     this node's uid
     * @param executor builds the patches one after another, off the main thread
     * @param logger   logger of the plugin
     */
    public PatchFeed(Views views, GatewayLink link, UUID node, Executor executor, Logger logger) {
        this.views = views;
        this.link = link;
        this.node = node;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * The sequence of the last patch, which a snapshot built now carries.
     *
     * @return the sequence
     */
    public long sequence() {
        return sequence.get();
    }

    /**
     * Starts pushing patches for a session.
     *
     * @param session the session id
     * @param player  the session's player
     * @param company the player's company
     */
    public void subscribe(String session, UUID player, Optional<Integer> company) {
        subscribers.put(session, new Subscriber(session, player, company.orElse(0)));
    }

    /**
     * Stops pushing patches for a session.
     *
     * @param session the session id
     */
    public void unsubscribe(String session) {
        subscribers.remove(session);
    }

    /**
     * Stops pushing patches for every session, after the connection closed.
     */
    public void clear() {
        subscribers.clear();
    }

    /**
     * @return the number of subscribed sessions
     */
    public int subscriptions() {
        return subscribers.size();
    }

    /**
     * The subscribed sessions.
     *
     * @return the session ids
     */
    public Set<String> subscribed() {
        return Set.copyOf(subscribers.keySet());
    }

    /**
     * An order changed: its owner and the company working on it see the new state.
     *
     * @param order    the order as the event carried it
     * @param previous a company that no longer works on it, e.g. after it expired or was cancelled
     */
    public void orderChanged(ISimpleOrder order, @Nullable Integer previous) {
        run(() -> {
            var current = views.order(order.id());
            var key = String.valueOf(order.id());
            var owners = sessions(subscriber -> subscriber.player.equals(order.owner()));
            if (current.isEmpty() || current.get().state() == OrderState.RECEIVED) {
                emit(owners, "orders", PatchOp.REMOVE, key, null);
            } else {
                emit(owners, "orders", PatchOp.UPSERT, key, views.own(current.get()));
            }
            var company = current.map(ISimpleOrder::company).orElse(0);
            if (company > 0) {
                var members = ids(members(company));
                if (current.get().state() == OrderState.CLAIMED) {
                    emit(members, "company.orders", PatchOp.UPSERT, key, views.claimed(current.get()));
                } else {
                    emit(members, "company.orders", PatchOp.REMOVE, key, null);
                }
            }
            if (previous != null && previous > 0 && previous != company) {
                emit(ids(members(previous)), "company.orders", PatchOp.REMOVE, key, null);
            }
        });
    }

    /**
     * A member of a company changed, e.g. delivered items or got other permissions: the members see the member with
     * the new statistics and permissions.
     *
     * @param company the company id
     * @param member  the member
     */
    public void memberChanged(int company, UUID member) {
        run(() -> {
            var profile = views.company(company);
            var members = ids(members(profile, company));
            profile.flatMap(found -> views.member(found, member))
                    .ifPresent(view -> emit(members, "company.members", PatchOp.UPSERT, member.toString(), view));
        });
    }

    /**
     * A player joined a company: the player sees the company, its members see the new member.
     *
     * @param company the company id
     * @param member  the new member
     */
    public void memberJoined(int company, UUID member) {
        run(() -> {
            refreshPlayer(member);
            var profile = views.company(company);
            var others = ids(members(profile, company).stream()
                    .filter(subscriber -> !subscriber.player.equals(member))
                    .toList());
            profile.flatMap(found -> views.member(found, member))
                    .ifPresent(view -> emit(others, "company.members", PatchOp.UPSERT, member.toString(), view));
        });
    }

    /**
     * A member left or was kicked: the player loses the company, its members lose the member.
     *
     * @param company the company id
     * @param member  the former member
     */
    public void memberLeft(int company, UUID member) {
        run(() -> {
            refreshPlayer(member);
            emit(ids(members(company)), "company.members", PatchOp.REMOVE, member.toString(), null);
        });
    }

    /**
     * A company changed as a whole, e.g. its profile, level or owner: its members see the new company and their own
     * permissions in it, and sessions that still counted a former member to it see the player without it.
     *
     * @param company the company id
     */
    public void companyChanged(int company) {
        run(() -> {
            var profile = views.company(company);
            var stale = sessionsOf(subscriber -> subscriber.company == company);
            var members = members(profile, company);
            for (var subscriber : stale) {
                if (!members.contains(subscriber)) refreshPlayer(subscriber.player);
            }
            for (var subscriber : members) {
                emit(
                        List.of(subscriber.session),
                        "company",
                        PatchOp.REPLACE,
                        null,
                        views.company(profile.orElseThrow(), subscriber.player));
                emit(
                        List.of(subscriber.session),
                        "player",
                        PatchOp.REPLACE,
                        null,
                        views.player(subscriber.player, profile));
            }
        });
    }

    /**
     * A player's own values changed, e.g. their company or permissions in it.
     *
     * @param player the player
     */
    public void playerChanged(UUID player) {
        run(() -> refreshPlayer(player));
    }

    private void refreshPlayer(UUID player) {
        var sessions = sessions(subscriber -> subscriber.player.equals(player));
        if (sessions.isEmpty()) return;
        var company = views.companyOf(player);
        var id = company.map(ICompanyProfile::id).orElse(0);
        for (var subscriber : subscribers.values()) {
            if (subscriber.player.equals(player)) subscriber.company = id;
        }
        emit(sessions, "player", PatchOp.REPLACE, null, views.player(player, company));
        for (var session : sessions) {
            if (company.isPresent()) {
                emit(List.of(session), "company", PatchOp.REPLACE, null, views.company(company.get(), player));
            } else {
                emit(List.of(session), "company", PatchOp.REMOVE, null, null);
            }
        }
    }

    /**
     * A player was invited, or an invite ended: the player sees the open invites.
     *
     * @param player the invited player
     */
    public void invitesChanged(UUID player) {
        run(() -> {
            var sessions = sessions(subscriber -> subscriber.player.equals(player));
            if (sessions.isEmpty()) return;
            emit(sessions, "player.invites", PatchOp.REPLACE, null, views.invites(player));
        });
    }

    /**
     * The ranking may have changed. The sessions see it on the next {@link #pushRankings()}, so a burst of completed
     * orders sends the ranking once.
     */
    public void rankingsChanged() {
        rankingsDirty.set(true);
    }

    /**
     * Sends the first ranking page to every session when it may have changed since the last push. The page is built
     * once; each session gets it with the place of the company its player is a member of now.
     */
    public void pushRankings() {
        if (subscribers.isEmpty() || !rankingsDirty.getAndSet(false)) return;
        run(() -> {
            var page = views.ranking(Views.DEFAULT_RANKING, Views.DEFAULT_RANGE, 1, Optional.empty());
            Map<Integer, Optional<RankingEntry>> own = new HashMap<>();
            Map<UUID, Integer> companies = new HashMap<>();
            for (var subscriber : List.copyOf(subscribers.values())) {
                int company = companies.computeIfAbsent(subscriber.player, player -> views.companyOf(player)
                        .map(ICompanyProfile::id)
                        .orElse(0));
                var place = company > 0
                        ? own.computeIfAbsent(company, id -> views.rank(Views.DEFAULT_RANKING, Views.DEFAULT_RANGE, id))
                        : Optional.<RankingEntry>empty();
                emit(
                        List.of(subscriber.session),
                        "rankings",
                        PatchOp.REPLACE,
                        null,
                        new RankingPage(
                                page.ranking(),
                                page.range(),
                                page.page(),
                                page.pages(),
                                page.entries(),
                                place.orElse(null)));
            }
        });
    }

    /**
     * A player's inbox got an entry.
     *
     * @param player the player
     */
    public void inboxChanged(UUID player) {
        run(() -> {
            var sessions = sessions(subscriber -> subscriber.player.equals(player));
            if (sessions.isEmpty()) return;
            emit(sessions, "player.unreadInbox", PatchOp.REPLACE, null, views.unreadInbox(player));
        });
    }

    /**
     * Money moved in a company's treasury: members with the treasury permission see the new balance.
     *
     * @param company the company id
     */
    public void treasuryChanged(int company) {
        run(() -> {
            var profile = views.company(company);
            if (profile.isEmpty()) return;
            for (var subscriber : members(profile, company)) {
                views.treasury(profile.get(), subscriber.player)
                        .ifPresent(treasury ->
                                emit(List.of(subscriber.session), "company.treasury", PatchOp.REPLACE, null, treasury));
            }
        });
    }

    /**
     * The subscribed members of a company by the company's current members, not by what the subscription remembered:
     * a membership that changed on another node is noticed here. The remembered company is corrected on the way.
     *
     * @param company the company id
     * @return the subscribers who are members
     */
    private List<Subscriber> members(int company) {
        return members(views.company(company), company);
    }

    private List<Subscriber> members(Optional<ICompanyProfile> profile, int company) {
        List<Subscriber> members = new ArrayList<>();
        for (var subscriber : subscribers.values()) {
            var member = profile.flatMap(found -> Views.memberOf(found, subscriber.player))
                    .isPresent();
            if (member) {
                if (subscriber.company != company) introduce(subscriber, profile.orElseThrow());
                subscriber.company = company;
                members.add(subscriber);
            } else if (subscriber.company == company) {
                subscriber.company = 0;
            }
        }
        return members;
    }

    /**
     * Sends a session the whole company and player before any patch below {@code company}, when it joined a company
     * this node did not know it was in. The gateway applies {@code company.*} patches only to a snapshot that has a
     * company, and does not check which one.
     *
     * @param subscriber the session
     * @param company    its company now
     */
    private void introduce(Subscriber subscriber, ICompanyProfile company) {
        emit(
                List.of(subscriber.session),
                "player",
                PatchOp.REPLACE,
                null,
                views.player(subscriber.player, Optional.of(company)));
        emit(List.of(subscriber.session), "company", PatchOp.REPLACE, null, views.company(company, subscriber.player));
    }

    private List<Subscriber> sessionsOf(Predicate<Subscriber> filter) {
        return subscribers.values().stream().filter(filter).toList();
    }

    private static List<String> ids(List<Subscriber> subscribers) {
        return subscribers.stream().map(subscriber -> subscriber.session).toList();
    }

    private List<String> sessions(Predicate<Subscriber> filter) {
        List<String> sessions = new ArrayList<>();
        for (var subscriber : subscribers.values()) {
            if (filter.test(subscriber)) sessions.add(subscriber.session);
        }
        return sessions;
    }

    private void emit(List<String> sessions, String path, PatchOp op, @Nullable String key, @Nullable Object value) {
        if (sessions.isEmpty()) return;
        JsonNode tree = value == null ? null : mapper.valueToTree(value);
        link.send(new Patch(
                null, Protocol.VERSION, List.copyOf(sessions), node, sequence.incrementAndGet(), path, op, key, tree));
    }

    private void run(Runnable task) {
        if (subscribers.isEmpty()) return;
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not update the open dashboards.", e);
            }
        });
    }

    /**
     * A subscribed session.
     */
    private static final class Subscriber {
        private final String session;
        private final UUID player;
        private volatile int company;

        Subscriber(String session, UUID player, int company) {
            this.session = Objects.requireNonNull(session);
            this.player = Objects.requireNonNull(player);
            this.company = company;
        }
    }
}
