/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.Subscribe;
import de.eldoria.companies.web.api.update.Unsubscribe;
import de.eldoria.companies.web.gateway.browser.BrowserStreams;
import de.eldoria.companies.web.gateway.browser.GatewayEvent;
import de.eldoria.companies.web.gateway.node.LiveServer;
import de.eldoria.companies.web.gateway.node.NodeHub;
import de.eldoria.companies.web.gateway.node.NodeLink;
import de.eldoria.companies.web.gateway.node.NodeRequestException;
import de.eldoria.companies.web.gateway.node.Reply;
import de.eldoria.companies.web.gateway.storage.StoredSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The snapshots of the sessions browsers use, in memory only.
 * <p>
 * The first access of a session asks the server's routed node for its snapshot ({@code snapshot.request}, which
 * subscribes that node) and sends {@code subscribe} to the other ready nodes, since every node pushes the patches of
 * its own events. Patches are applied in the order of their node's sequence; one at or below the sequence the
 * snapshot was built at is already in it. A gap in a node's sequence, a patch that does not fit, or a node that
 * connects again loads the snapshots of that server again; patches arriving meanwhile are kept and applied on top.
 * <p>
 * The snapshot is kept as the JSON the node sent, so fields of a newer plugin reach the browser. A patch into a part
 * of the snapshot this version does not know is passed on even when it cannot be applied here, rather than loading the
 * snapshot again.
 * <p>
 * When a patch changes the session's company, the other ready nodes are sent {@code subscribe} again, so they push for
 * the new company's members and orders and stop pushing for the old one's.
 * <p>
 * Applied patches and replaced snapshots also drop the session's cached read answers in {@link ReadRelay} that they may
 * change.
 * <p>
 * Browsers get each applied patch over their stream, addressed to their session only, and a {@code resync} when the
 * snapshot was replaced. Patches only upsert, replace or remove by key, so a browser that applies a patch its fetched
 * snapshot already holds ends in the same state.
 * <p>
 * A snapshot no browser used for the cache time is dropped and every node of the server is sent {@code unsubscribe};
 * the next access loads it again. The gateway never changes a snapshot on its own.
 */
public final class SnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(SnapshotCache.class);
    private static final JsonMapper MAPPER = Protocol.mapper();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final NodeHub hub;
    private final BrowserStreams streams;
    private final ReadRelay reads;
    private final Clock clock;
    private final Duration idle;
    private final Duration timeout;

    private static final class Entry {
        private final String sessionId;
        private final String serverId;
        private final UUID player;
        private final List<Patch> buffered = new ArrayList<>();
        private Map<UUID, Long> base = Map.of();
        private @Nullable ObjectNode snapshot;
        private @Nullable CompletableFuture<JsonNode> loading;
        private volatile Instant lastAccess;

        private Entry(StoredSession session, Instant now) {
            this.sessionId = session.id();
            this.serverId = session.serverId();
            this.player = session.player();
            this.lastAccess = now;
        }
    }

    /**
     * @param hub the node connections
     * @param streams the browser streams
     * @param reads the cached read answers to drop when a patch changes them
     * @param clock the time
     * @param idle how long a snapshot stays without a browser using it
     * @param timeout how long to wait for a node's snapshot
     */
    public SnapshotCache(
            NodeHub hub, BrowserStreams streams, ReadRelay reads, Clock clock, Duration idle, Duration timeout) {
        this.hub = hub;
        this.streams = streams;
        this.reads = reads;
        this.clock = clock;
        this.idle = idle;
        this.timeout = timeout;
    }

    /**
     * Returns a session's snapshot, loading it from a node when it is not in memory.
     *
     * @param session the session
     * @return the {@code snapshot} message with every patch since applied; fails with {@link NodeRequestException}
     * when no node can provide it
     */
    public CompletableFuture<JsonNode> snapshot(StoredSession session) {
        Instant now = clock.instant();
        Entry entry = entries.computeIfAbsent(session.id(), id -> new Entry(session, now));
        entry.lastAccess = now;
        synchronized (entry) {
            ObjectNode snapshot = entry.snapshot;
            if (snapshot != null && entry.loading == null) {
                return CompletableFuture.completedFuture(snapshot.deepCopy());
            }
        }
        return load(entry);
    }

    /**
     * Records that a browser used a session.
     *
     * @param sessionId the session
     */
    public void touch(String sessionId) {
        Entry entry = entries.get(sessionId);
        if (entry != null) entry.lastAccess = clock.instant();
    }

    /**
     * A node pushed a patch.
     *
     * @param server the server
     * @param link the node
     * @param patch the patch
     */
    public void patch(LiveServer server, NodeLink link, Patch patch) {
        switch (link.sequence(patch.seq())) {
            case STALE -> {
                return;
            }
            case GAP -> {
                log.debug(
                        "Node {} skipped patches before {}, reloading",
                        link.agreed().uid(),
                        patch.seq());
                reloadServer(server.id());
            }
            case NEXT -> {}
        }
        if (patch.sessions() == null) return;
        for (String sessionId : patch.sessions()) {
            Entry entry = entries.get(sessionId);
            if (entry != null && entry.serverId.equals(server.id())) apply(entry, patch);
        }
    }

    /**
     * A node of a server was welcomed: it is subscribed to the server's cached sessions, and their snapshots are
     * loaded again, as patches may have been missed while it was away.
     *
     * @param server the server
     * @param link the node
     */
    public void nodeReady(LiveServer server, NodeLink link) {
        for (Entry entry : List.copyOf(entries.values())) {
            if (!entry.serverId.equals(server.id())) continue;
            link.send(new Subscribe(link.nextId(), link.agreed().protocol(), entry.sessionId, entry.player));
            reload(entry);
        }
    }

    /**
     * A node's version was discontinued while it was connected: it is sent {@code unsubscribe} for the server's cached
     * sessions, as it takes no requests and pushes no patches any more.
     *
     * @param server the server
     * @param link the node
     */
    public void nodeDiscontinued(LiveServer server, NodeLink link) {
        for (Entry entry : List.copyOf(entries.values())) {
            if (!entry.serverId.equals(server.id())) continue;
            link.send(new Unsubscribe(link.nextId(), link.agreed().protocol(), entry.sessionId));
        }
    }

    /**
     * Drops the snapshot of a session that ended and sends {@code unsubscribe} to every ready node of its server, as
     * each node pushes the patches of its own events.
     *
     * @param serverId the session's server
     * @param sessionId the session
     */
    public void end(String serverId, String sessionId) {
        entries.remove(sessionId);
        unsubscribe(serverId, sessionId);
    }

    /**
     * Drops snapshots no browser used for the cache time and unsubscribes the nodes.
     */
    public void sweep() {
        Instant limit = clock.instant().minus(idle);
        for (Entry entry : List.copyOf(entries.values())) {
            if (!entry.lastAccess.isBefore(limit) || !entries.remove(entry.sessionId, entry)) continue;
            unsubscribe(entry.serverId, entry.sessionId);
        }
    }

    private void unsubscribe(String serverId, String sessionId) {
        hub.server(serverId).ifPresent(server -> {
            for (NodeLink link : server.ready()) {
                link.send(new Unsubscribe(link.nextId(), link.agreed().protocol(), sessionId));
            }
        });
    }

    private CompletableFuture<JsonNode> load(Entry entry) {
        synchronized (entry) {
            CompletableFuture<JsonNode> loading = entry.loading;
            if (loading != null) return loading;
            Optional<LiveServer> server = hub.server(entry.serverId);
            Optional<NodeLink> routed = server.flatMap(LiveServer::route);
            if (server.isEmpty() || routed.isEmpty()) {
                if (entry.snapshot == null) entries.remove(entry.sessionId, entry);
                return CompletableFuture.failedFuture(new NodeRequestException(null, "The server is not connected"));
            }
            NodeLink link = routed.get();
            boolean replacing = entry.snapshot != null;
            CompletableFuture<JsonNode> request = link.request(
                            id -> new SnapshotRequest(id, link.agreed().protocol(), entry.sessionId, entry.player),
                            timeout)
                    .thenApply(reply -> loaded(entry, reply, replacing));
            entry.loading = request;
            request.whenComplete((snapshot, error) -> {
                if (error == null) return;
                synchronized (entry) {
                    if (entry.loading == request) entry.loading = null;
                    entry.buffered.clear();
                    if (entry.snapshot == null) entries.remove(entry.sessionId, entry);
                }
            });
            for (NodeLink other : server.get().ready()) {
                if (other == link) continue;
                other.send(new Subscribe(other.nextId(), other.agreed().protocol(), entry.sessionId, entry.player));
            }
            return request;
        }
    }

    private JsonNode loaded(Entry entry, Reply reply, boolean replacing) {
        if (!(reply.message() instanceof Snapshot snapshot)) {
            throw new NodeRequestException(
                    null,
                    "The server answered with a " + reply.message().getClass().getSimpleName());
        }
        ObjectNode tree = reply.raw().deepCopy();
        JsonNode copy;
        boolean stale = false;
        synchronized (entry) {
            entry.snapshot = tree;
            entry.base = Map.of(snapshot.node(), snapshot.seq());
            for (Patch patch : entry.buffered) {
                if (!applies(entry, patch)) continue;
                if (PatchApplier.foreignCompany(tree, patch)) {
                    stale = true;
                    continue;
                }
                try {
                    PatchApplier.apply(tree, patch);
                } catch (PatchApplier.Unapplicable e) {
                    stale |= PatchApplier.knownRoot(patch);
                }
            }
            entry.buffered.clear();
            entry.loading = null;
            copy = tree.deepCopy();
        }
        if (replacing) {
            reads.drop(entry.sessionId);
            streams.push(entry.sessionId, GatewayEvent.resync());
        }
        if (stale) CompletableFuture.runAsync(() -> reload(entry));
        return copy;
    }

    private void apply(Entry entry, Patch patch) {
        boolean companyChanged;
        synchronized (entry) {
            if (entry.loading != null) {
                entry.buffered.add(patch);
                return;
            }
            ObjectNode snapshot = entry.snapshot;
            if (snapshot == null || !applies(entry, patch)) return;
            if (PatchApplier.foreignCompany(snapshot, patch)) {
                log.warn("A patch of {} reaches into a company the session has none of, reloading", patch.path());
                reload(entry);
                return;
            }
            String companyBefore = PatchApplier.company(snapshot);
            try {
                PatchApplier.apply(snapshot, patch);
            } catch (PatchApplier.Unapplicable e) {
                if (PatchApplier.knownRoot(patch)) {
                    log.debug("A patch of {} does not fit, reloading: {}", patch.path(), e.getMessage());
                    reload(entry);
                    return;
                }
                log.debug("Passing on a patch of {}, which this version does not know", patch.path());
            }
            companyChanged = !companyBefore.equals(PatchApplier.company(snapshot));
        }
        reads.patched(entry.sessionId, patch.path());
        if (companyChanged) resubscribe(entry, patch.node());
        streams.push(
                entry.sessionId,
                new Patch(
                        null,
                        patch.v(),
                        List.of(entry.sessionId),
                        patch.node(),
                        patch.seq(),
                        patch.path(),
                        patch.op(),
                        patch.key(),
                        patch.value()));
    }

    /**
     * The session's player changed company on one node; the other nodes still push for the old one until they look
     * at the membership again, which a {@code subscribe} makes them do.
     */
    private void resubscribe(Entry entry, UUID changedBy) {
        hub.server(entry.serverId).ifPresent(server -> {
            for (NodeLink link : server.ready()) {
                if (link.agreed().uid().equals(changedBy)) continue;
                link.send(new Subscribe(link.nextId(), link.agreed().protocol(), entry.sessionId, entry.player));
            }
        });
    }

    private static boolean applies(Entry entry, Patch patch) {
        Long base = entry.base.get(patch.node());
        return base == null || patch.seq() > base;
    }

    private void reloadServer(String serverId) {
        for (Entry entry : List.copyOf(entries.values())) {
            if (entry.serverId.equals(serverId)) reload(entry);
        }
    }

    private void reload(Entry entry) {
        load(entry).exceptionally(error -> {
            log.debug(
                    "Reloading the snapshot of a session failed: {}",
                    NodeRequestException.of(error).getMessage());
            return null;
        });
    }
}
