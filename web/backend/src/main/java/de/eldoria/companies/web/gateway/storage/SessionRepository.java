/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Stores dashboard sessions, pending and bound, so a restart of the gateway keeps players logged in. Times are passed
 * in by the caller, so the gateway's clock decides what has expired.
 */
public final class SessionRepository {
    private static final String COLUMNS = """
            id, server_id, player::TEXT AS player, player_name, node::TEXT AS node, state, short_id, code_hash,
            attempts, code_expires, device, admin, created, bound_at, last_access""";

    /**
     * Creates a pending session unless its short id is taken.
     *
     * @param id the session id
     * @param serverId the server
     * @param player the player's id
     * @param playerName the player's name
     * @param node the node the player is on
     * @param shortId the short id of the link
     * @param codeHash the hash of the login code
     * @param codeExpires when the code stops working
     * @param now the time
     * @return whether it was created; false when the short id is taken
     */
    public boolean createPending(
            String id,
            String serverId,
            UUID player,
            String playerName,
            UUID node,
            String shortId,
            String codeHash,
            Instant codeExpires,
            Instant now) {
        return query("""
                INSERT INTO session(id, server_id, player, player_name, node, state, short_id, code_hash, code_expires,
                                    created, last_access)
                VALUES (:id, :server, CAST(:player AS UUID), :name, CAST(:node AS UUID), :state, :short, :code, :expires,
                        :now, :now)
                ON CONFLICT (short_id) DO NOTHING;""")
                .single(call().bind("id", id)
                        .bind("server", serverId)
                        .bind("player", player.toString())
                        .bind("name", playerName)
                        .bind("node", node.toString())
                        .bind("state", SessionState.PENDING.name())
                        .bind("short", shortId)
                        .bind("code", codeHash)
                        .bind("expires", Timestamp.from(codeExpires))
                        .bind("now", Timestamp.from(now)))
                .insert()
                .changed();
    }

    /**
     * @param shortId the short id of a link
     * @param now the time
     * @return the pending session of that link whose code still works
     */
    public Optional<StoredSession> findPending(String shortId, Instant now) {
        return query(
                        "SELECT %s FROM session WHERE short_id = :short AND state <> :bound AND code_expires > :now;",
                        COLUMNS)
                .single(call().bind("short", shortId)
                        .bind("bound", SessionState.BOUND.name())
                        .bind("now", Timestamp.from(now)))
                .map(SessionRepository::map)
                .first();
    }

    /**
     * @param id the session id
     * @return the session in any state
     */
    public Optional<StoredSession> find(String id) {
        return query("SELECT %s FROM session WHERE id = :id;", COLUMNS)
                .single(call().bind("id", id))
                .map(SessionRepository::map)
                .first();
    }

    /**
     * Takes one of a pending session's attempts before its code is compared, so concurrent tries cannot exceed
     * the limit.
     *
     * @param id the session id
     * @param max the most attempts a session has
     * @return the attempts used including this one; none when the session is not pending or has none left
     */
    public Optional<Integer> reserveAttempt(String id, int max) {
        return query("""
                UPDATE session SET attempts = attempts + 1
                WHERE id = :id AND state = :pending AND attempts < :max
                RETURNING attempts;""")
                .single(call().bind("id", id)
                        .bind("pending", SessionState.PENDING.name())
                        .bind("max", max))
                .map(row -> row.getInt("attempts"))
                .first();
    }

    /**
     * Returns an attempt a right code took when another browser opened the session first.
     *
     * @param id the session id
     */
    public void refundAttempt(String id) {
        query("UPDATE session SET attempts = greatest(attempts - 1, 0) WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Moves a pending session to {@link SessionState#OPENING}, so only one browser logs in with a code.
     *
     * @param id the session id
     * @return whether the session was pending
     */
    public boolean startOpening(String id) {
        return transition(id, SessionState.PENDING, SessionState.OPENING);
    }

    /**
     * Moves an opening session back to pending after the plugin did not answer, and returns the attempt the right
     * code took.
     *
     * @param id the session id
     */
    public void backToPending(String id) {
        query("""
                UPDATE session SET state = :pending, attempts = greatest(attempts - 1, 0)
                WHERE id = :id AND state = :opening;""")
                .single(call().bind("id", id)
                        .bind("pending", SessionState.PENDING.name())
                        .bind("opening", SessionState.OPENING.name()))
                .update();
    }

    private boolean transition(String id, SessionState from, SessionState to) {
        return query("UPDATE session SET state = :to WHERE id = :id AND state = :from;")
                .single(call().bind("id", id).bind("from", from.name()).bind("to", to.name()))
                .update()
                .changed();
    }

    /**
     * Binds an opening session to a browser: the link and code stop working, the cookie starts.
     *
     * @param id the session id
     * @param cookieHash the hash of the cookie value
     * @param device the browser
     * @param admin whether the player has admin rights, as the plugin said
     * @param now the time
     * @return whether the session was opening
     */
    public boolean bind(String id, String cookieHash, @Nullable String device, boolean admin, Instant now) {
        return query("""
                UPDATE session
                SET state = :bound, short_id = NULL, code_hash = NULL, code_expires = NULL, cookie_hash = :cookie,
                    device = :device, admin = :admin, bound_at = :now, last_access = :now
                WHERE id = :id AND state = :opening;""")
                .single(call().bind("id", id)
                        .bind("bound", SessionState.BOUND.name())
                        .bind("opening", SessionState.OPENING.name())
                        .bind("cookie", cookieHash)
                        .bind("device", device)
                        .bind("admin", admin)
                        .bind("now", Timestamp.from(now)))
                .update()
                .changed();
    }

    /**
     * @param cookieHash the hash of a cookie value
     * @param idleSince sessions not used since then are expired
     * @param boundSince sessions bound before then are expired
     * @return the live bound session of that cookie
     */
    public Optional<StoredSession> findBound(String cookieHash, Instant idleSince, Instant boundSince) {
        return query("""
                SELECT %s FROM session
                WHERE cookie_hash = :cookie AND state = :bound AND last_access > :idle AND bound_at > :max;""", COLUMNS)
                .single(call().bind("cookie", cookieHash)
                        .bind("bound", SessionState.BOUND.name())
                        .bind("idle", Timestamp.from(idleSince))
                        .bind("max", Timestamp.from(boundSince)))
                .map(SessionRepository::map)
                .first();
    }

    /**
     * Records that a browser used a session.
     *
     * @param id the session id
     * @param now the time
     */
    public void touch(String id, Instant now) {
        query("UPDATE session SET last_access = :now WHERE id = :id AND last_access < :now;")
                .single(call().bind("id", id).bind("now", Timestamp.from(now)))
                .update();
    }

    /**
     * @param id the session id
     * @return the deleted session
     */
    public Optional<StoredSession> delete(String id) {
        return query("DELETE FROM session WHERE id = :id RETURNING %s;", COLUMNS)
                .single(call().bind("id", id))
                .map(SessionRepository::map)
                .first();
    }

    /**
     * @param serverId the server
     * @param player the player
     * @return the deleted sessions of the player on that server
     */
    public List<StoredSession> deleteOfPlayer(String serverId, UUID player) {
        return query(
                        "DELETE FROM session WHERE server_id = :server AND player = CAST(:player AS UUID) RETURNING %s;",
                        COLUMNS)
                .single(call().bind("server", serverId).bind("player", player.toString()))
                .map(SessionRepository::map)
                .all();
    }

    /**
     * Deletes pending and opening sessions whose code expired.
     *
     * @param now the time
     * @return the deleted sessions
     */
    public List<StoredSession> deleteExpiredCodes(Instant now) {
        return query("DELETE FROM session WHERE state <> :bound AND code_expires <= :now RETURNING %s;", COLUMNS)
                .single(call().bind("bound", SessionState.BOUND.name()).bind("now", Timestamp.from(now)))
                .map(SessionRepository::map)
                .all();
    }

    /**
     * Deletes bound sessions no browser used since a time.
     *
     * @param idleSince the time
     * @return the deleted sessions
     */
    public List<StoredSession> deleteIdle(Instant idleSince) {
        return query("DELETE FROM session WHERE state = :bound AND last_access <= :idle RETURNING %s;", COLUMNS)
                .single(call().bind("bound", SessionState.BOUND.name()).bind("idle", Timestamp.from(idleSince)))
                .map(SessionRepository::map)
                .all();
    }

    /**
     * Deletes bound sessions bound before a time.
     *
     * @param boundSince the time
     * @return the deleted sessions
     */
    public List<StoredSession> deleteOlderThan(Instant boundSince) {
        return query("DELETE FROM session WHERE state = :bound AND bound_at <= :max RETURNING %s;", COLUMNS)
                .single(call().bind("bound", SessionState.BOUND.name()).bind("max", Timestamp.from(boundSince)))
                .map(SessionRepository::map)
                .all();
    }

    /**
     * Deletes the sessions of servers that have been disconnected since before a time.
     *
     * @param goneSince the time
     * @return the deleted sessions
     */
    public List<StoredSession> deleteOfServersGoneSince(Instant goneSince) {
        return query("""
                DELETE FROM session s USING server v
                WHERE s.server_id = v.id AND v.disconnected_at IS NOT NULL AND v.disconnected_at <= :gone
                RETURNING %s;""", COLUMNS.replace("id, server_id", "s.id, s.server_id"))
                .single(call().bind("gone", Timestamp.from(goneSince)))
                .map(SessionRepository::map)
                .all();
    }

    private static StoredSession map(Row row) throws SQLException {
        return new StoredSession(
                row.getString("id"),
                row.getString("server_id"),
                UUID.fromString(row.getString("player")),
                row.getString("player_name"),
                UUID.fromString(row.getString("node")),
                SessionState.valueOf(row.getString("state")),
                row.getString("short_id"),
                row.getString("code_hash"),
                row.getInt("attempts"),
                instant(row.getTimestamp("code_expires")),
                row.getString("device"),
                row.getBoolean("admin"),
                row.getTimestamp("created").toInstant(),
                instant(row.getTimestamp("bound_at")),
                row.getTimestamp("last_access").toInstant());
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
