/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.gateway.node.NodeLink;
import de.eldoria.companies.web.gateway.node.NodeRequestException;
import de.eldoria.companies.web.gateway.storage.StoredSession;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forwards a browser's read requests, such as an order search or an inbox page, to a node as {@code action}s and
 * keeps successful answers per session, action and arguments for {@value #CACHE_SECONDS} seconds. Search results are
 * not live; a browser asks again to refresh them.
 * <p>
 * Patches applied to a session's snapshot drop the session's cached answers they may change, as a browser that reads
 * again after such a change, for example the treasury log after a balance change, expects to see it:
 * <ul>
 *   <li>a patch below {@code player}, {@code company} or {@code orders}, or a reloaded snapshot, drops all of
 *   them;</li>
 *   <li>a patch below {@code rankings} drops the answers of the {@code rankings.*} actions;</li>
 *   <li>any other patch keeps them.</li>
 * </ul>
 * Dropping replaces the session's answers rather than emptying them, so an answer to a request sent before the drop
 * is stored where it is never served.
 * <p>
 * Paging arguments are kept within the node's limits: {@code page} is at least 1 and {@code pageSize} (or
 * {@code size}) at most the node's {@code maxPageSize}.
 */
public final class ReadRelay {
    /**
     * The actions that only read and may be forwarded.
     */
    public static final Set<String> READ_ACTIONS = Set.of(
            Actions.ORDERS_SEARCH,
            Actions.ORDERS_DETAILS,
            Actions.ORDERS_OWN_LIST,
            Actions.APPLICATIONS_LIST,
            Actions.TREASURY_LOG,
            Actions.INBOX_PAGE,
            Actions.RANKINGS_PAGE,
            Actions.RANKINGS_OWN,
            Actions.RANKINGS_HISTORY,
            Actions.MARKET_ITEM,
            Actions.MARKET_SEARCH,
            Actions.MARKET_TOP,
            Actions.MARKET_MOVERS);

    private static final long CACHE_SECONDS = 30;
    private static final Set<String> SESSION_ROOTS = Set.of("player", "company", "orders");
    private static final String RANKINGS_ROOT = "rankings";
    private static final List<String> PAGE_SIZES = List.of("pageSize", "size");
    private final Map<String, Map<String, Cached>> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration timeout;

    private record Cached(String action, ObjectNode result, Instant at) {}

    /**
     * @param clock the time
     * @param timeout how long to wait for the node's answer
     */
    public ReadRelay(Clock clock, Duration timeout) {
        this.clock = clock;
        this.timeout = timeout;
    }

    /**
     * @param session the session asking
     * @param link the node to ask
     * @param name a read action of {@link #READ_ACTIONS}
     * @param args the action's arguments, changed in place to fit the node's limits
     * @return the node's {@code action.result} as it sent it; fails with {@link NodeRequestException} when the node
     * refuses or does not answer
     */
    public CompletableFuture<ObjectNode> read(StoredSession session, NodeLink link, String name, ObjectNode args) {
        limitPaging(args, link.maxPageSize());
        String key = name + '\n' + args;
        Map<String, Cached> answers = sessions.computeIfAbsent(session.id(), id -> new ConcurrentHashMap<>());
        Cached cached = answers.get(key);
        if (cached != null && fresh(cached, clock.instant())) {
            return CompletableFuture.completedFuture(cached.result().deepCopy());
        }
        return link.request(
                        id -> new ActionRequest(
                                id, link.agreed().protocol(), session.id(), session.player(), name, args),
                        timeout)
                .thenApply(reply -> {
                    if (!(reply.message() instanceof ActionResult result)) {
                        throw new NodeRequestException(null, "The server answered with something else");
                    }
                    if (result.ok()) answers.put(key, new Cached(name, reply.raw(), clock.instant()));
                    return reply.raw().deepCopy();
                });
    }

    /**
     * A patch was applied to a session's snapshot; the session's answers it may change are forgotten.
     *
     * @param sessionId the session
     * @param path the patch's dotted path
     */
    public void patched(String sessionId, @Nullable String path) {
        if (path == null) return;
        String root = path.split("\\.", 2)[0];
        if (SESSION_ROOTS.contains(root)) {
            drop(sessionId);
        } else if (RANKINGS_ROOT.equals(root)) {
            sessions.computeIfPresent(sessionId, (id, answers) -> withoutRankings(answers));
        }
    }

    /**
     * Forgets the answers of a session.
     *
     * @param sessionId the session
     */
    public void drop(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Forgets answers older than the cache time and sessions without answers.
     */
    public void sweep() {
        Instant now = clock.instant();
        for (Map<String, Cached> answers : sessions.values()) {
            answers.values().removeIf(cached -> !fresh(cached, now));
        }
        sessions.values().removeIf(Map::isEmpty);
    }

    private static Map<String, Cached> withoutRankings(Map<String, Cached> answers) {
        Map<String, Cached> kept = new ConcurrentHashMap<>();
        answers.forEach((key, cached) -> {
            if (!cached.action().startsWith(RANKINGS_ROOT + '.')) kept.put(key, cached);
        });
        return kept;
    }

    private static boolean fresh(Cached cached, Instant now) {
        return cached.at().isAfter(now.minusSeconds(CACHE_SECONDS));
    }

    private static void limitPaging(ObjectNode args, int maxPageSize) {
        JsonNode page = args.get("page");
        if (page != null && page.isNumber() && page.asInt() < 1) args.put("page", 1);
        for (String field : PAGE_SIZES) {
            JsonNode size = args.get(field);
            if (size != null && size.isNumber()) args.put(field, Math.clamp(size.asInt(), 1, maxPageSize));
        }
    }
}
