/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Stores the servers that connected, their branding, when their last node went away, and which plugin version each
 * node runs, so the use of deprecated versions can be counted.
 */
public final class ServerRepository {

    /**
     * A server as stored.
     *
     * @param id the server id
     * @param name its branding name, none for the default look
     * @param iconHash the hash of its icon, none without one
     * @param disconnectedAt when its last node went away, none while one is connected
     */
    public record StoredServer(
            String id,
            @Nullable String name,
            @Nullable String iconHash,
            @Nullable Instant disconnectedAt) {}

    /**
     * Records that a node of a server connected.
     *
     * @param id the server id
     * @param now the time
     */
    public void connected(String id, Instant now) {
        query("""
                INSERT INTO server(id, first_seen, last_seen) VALUES (:id, :now, :now)
                ON CONFLICT (id) DO UPDATE SET last_seen = :now, disconnected_at = NULL;""")
                .single(call().bind("id", id).bind("now", Timestamp.from(now)))
                .insert();
    }

    /**
     * Records a node of a server and the plugin version it runs.
     *
     * @param id the server id
     * @param node the node's uid
     * @param role its role
     * @param plugin its plugin version
     * @param protocol the protocol version used with it
     * @param now the time
     */
    public void node(String id, UUID node, String role, String plugin, int protocol, Instant now) {
        query("""
                INSERT INTO server_node(server_id, uid, role, plugin, protocol, last_seen)
                VALUES (:id, CAST(:uid AS UUID), :role, :plugin, :protocol, :now)
                ON CONFLICT (server_id, uid) DO UPDATE
                    SET role = :role, plugin = :plugin, protocol = :protocol, last_seen = :now;""")
                .single(call().bind("id", id)
                        .bind("uid", node.toString())
                        .bind("role", role)
                        .bind("plugin", plugin)
                        .bind("protocol", protocol)
                        .bind("now", Timestamp.from(now)))
                .insert();
    }

    /**
     * @param id the server id
     * @param name the branding name, none for the default look
     * @param iconHash the icon's hash, none without one
     */
    public void branding(String id, @Nullable String name, @Nullable String iconHash) {
        query("UPDATE server SET name = :name, icon_hash = :icon WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name).bind("icon", iconHash))
                .update();
    }

    /**
     * Records that the last node of a server went away.
     *
     * @param id the server id
     * @param now the time
     */
    public void disconnected(String id, Instant now) {
        query("UPDATE server SET disconnected_at = :now WHERE id = :id AND disconnected_at IS NULL;")
                .single(call().bind("id", id).bind("now", Timestamp.from(now)))
                .update();
    }

    /**
     * Marks every server disconnected, as none is connected when the gateway starts.
     *
     * @param now the time
     */
    public void allDisconnected(Instant now) {
        query("UPDATE server SET disconnected_at = :now WHERE disconnected_at IS NULL;")
                .single(call().bind("now", Timestamp.from(now)))
                .update();
    }

    /**
     * Moves the disconnect time of every disconnected server forward, so time the gateway was down does not count
     * towards the grace period after which their sessions end.
     *
     * @param downtime how long the gateway was down
     */
    public void excuseDisconnect(Duration downtime) {
        query("UPDATE server SET disconnected_at = disconnected_at + make_interval(secs => :secs)"
                        + " WHERE disconnected_at IS NOT NULL;")
                .single(call().bind("secs", (double) downtime.toSeconds()))
                .update();
    }

    /**
     * @param id the server id
     * @return the server
     */
    public Optional<StoredServer> find(String id) {
        return query("SELECT id, name, icon_hash, disconnected_at FROM server WHERE id = :id;")
                .single(call().bind("id", id))
                .map(row -> {
                    Timestamp gone = row.getTimestamp("disconnected_at");
                    return new StoredServer(
                            row.getString("id"),
                            row.getString("name"),
                            row.getString("icon_hash"),
                            gone == null ? null : gone.toInstant());
                })
                .first();
    }
}
