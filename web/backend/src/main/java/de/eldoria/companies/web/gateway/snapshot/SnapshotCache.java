/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.Subscribe;
import de.eldoria.companies.web.api.update.Unsubscribe;
import de.eldoria.companies.web.gateway.browser.BrowserStreams;
import de.eldoria.companies.web.gateway.browser.StreamEvent;
import de.eldoria.companies.web.gateway.node.LiveServer;
import de.eldoria.companies.web.gateway.node.NodeHub;
import de.eldoria.companies.web.gateway.node.NodeLink;
import de.eldoria.companies.web.gateway.node.NodeRequestException;
import de.eldoria.companies.web.gateway.storage.StoredSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Browsers get each applied patch, or a reload when the snapshot was replaced, over their stream.
 * <p>
 * A snapshot no browser used for the cache time is dropped and every node of the server is sent {@code unsubscribe};
 * the next access loads it again. The gateway never changes a snapshot on its own.
 */
public final class SnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(SnapshotCache.class);
    private static final JsonMapper MAPPER = Protocol.mapper();
    private static final List<String> ENVELOPE = List.of("type", "id", "v", "session", "node", "seq");
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final NodeHub hub;
    private final BrowserStreams streams;
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
        private @Nullable CompletableFuture<SnapshotView> loading;
        private long revision;
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
     * @param clock the time
     * @param idle how long a snapshot stays without a browser using it
     * @param timeout how long to wait for a node's snapshot
     */
    public SnapshotCache(NodeHub hub, BrowserStreams streams, Clock clock, Duration idle, Duration timeout) {
        this.hub = hub;
        this.streams = streams;
        this.clock = clock;
        this.idle = idle;
        this.timeout = timeout;
    }

    /**
     * Returns a session's snapshot, loading it from a node when it is not in memory.
     *
     * @param session the session
     * @return the snapshot; fails with {@link NodeRequestException} when no node can provide it
     */
    public CompletableFuture<SnapshotView> snapshot(StoredSession session) {
        Instant now = clock.instant();
        Entry entry = entries.computeIfAbsent(session.id(), id -> new Entry(session, now));
        entry.lastAccess = now;
        synchronized (entry) {
            ObjectNode snapshot = entry.snapshot;
            if (snapshot != null && entry.loading == null) {
                return CompletableFuture.completedFuture(new SnapshotView(entry.revision, snapshot.deepCopy()));
            }
        }
        return load(entry);
    }

    /**
     * @param sessionId a session
     * @return the session's snapshot while it is in memory and not being loaded
     */
    public Optional<SnapshotView> cached(String sessionId) {
        Entry entry = entries.get(sessionId);
        if (entry == null) return Optional.empty();
        synchronized (entry) {
            ObjectNode snapshot = entry.snapshot;
            if (snapshot == null || entry.loading != null) return Optional.empty();
            return Optional.of(new SnapshotView(entry.revision, snapshot.deepCopy()));
        }
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
     * Drops a session's snapshot without telling the nodes, because the session ended.
     *
     * @param sessionId the session
     */
    public void drop(String sessionId) {
        entries.remove(sessionId);
    }

    /**
     * Drops snapshots no browser used for the cache time and unsubscribes the nodes.
     */
    public void sweep() {
        Instant limit = clock.instant().minus(idle);
        for (Entry entry : List.copyOf(entries.values())) {
            if (!entry.lastAccess.isBefore(limit) || !entries.remove(entry.sessionId, entry)) continue;
            hub.server(entry.serverId).ifPresent(server -> {
                for (NodeLink link : server.ready()) {
                    link.send(new Unsubscribe(link.nextId(), link.agreed().protocol(), entry.sessionId));
                }
            });
        }
    }

    private CompletableFuture<SnapshotView> load(Entry entry) {
        synchronized (entry) {
            CompletableFuture<SnapshotView> loading = entry.loading;
            if (loading != null) return loading;
            Optional<LiveServer> server = hub.server(entry.serverId);
            Optional<NodeLink> routed = server.flatMap(LiveServer::route);
            if (server.isEmpty() || routed.isEmpty()) {
                if (entry.snapshot == null) entries.remove(entry.sessionId, entry);
                return CompletableFuture.failedFuture(new NodeRequestException(null, "The server is not connected"));
            }
            NodeLink link = routed.get();
            boolean replacing = entry.snapshot != null;
            CompletableFuture<SnapshotView> request = link.request(
                            id -> new SnapshotRequest(id, link.agreed().protocol(), entry.sessionId, entry.player),
                            timeout)
                    .thenApply(answer -> loaded(entry, answer, replacing));
            entry.loading = request;
            request.whenComplete((view, error) -> {
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

    private SnapshotView loaded(Entry entry, Message answer, boolean replacing) {
        if (!(answer instanceof Snapshot snapshot)) {
            throw new NodeRequestException(
                    null, "The server answered with a " + answer.getClass().getSimpleName());
        }
        ObjectNode tree = MAPPER.valueToTree(snapshot);
        ENVELOPE.forEach(tree::remove);
        SnapshotView view;
        boolean stale = false;
        synchronized (entry) {
            entry.snapshot = tree;
            Map<UUID, Long> base = new HashMap<>();
            base.put(snapshot.node(), snapshot.seq());
            entry.base = base;
            entry.revision++;
            for (Patch patch : entry.buffered) {
                if (!applies(entry, patch)) continue;
                try {
                    PatchApplier.apply(tree, patch);
                } catch (PatchApplier.Unapplicable e) {
                    stale = true;
                }
            }
            entry.buffered.clear();
            entry.loading = null;
            view = new SnapshotView(entry.revision, tree.deepCopy());
        }
        if (replacing) streams.push(entry.sessionId, StreamEvent.reload(view.revision()));
        if (stale) CompletableFuture.runAsync(() -> reload(entry));
        return view;
    }

    private void apply(Entry entry, Patch patch) {
        StreamEvent event;
        synchronized (entry) {
            if (entry.loading != null) {
                entry.buffered.add(patch);
                return;
            }
            ObjectNode snapshot = entry.snapshot;
            if (snapshot == null || !applies(entry, patch)) return;
            try {
                PatchApplier.apply(snapshot, patch);
            } catch (PatchApplier.Unapplicable e) {
                log.debug("A patch of {} does not fit, reloading: {}", patch.path(), e.getMessage());
                reload(entry);
                return;
            }
            entry.revision++;
            event = StreamEvent.patch(entry.revision, patch);
        }
        streams.push(entry.sessionId, event);
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
