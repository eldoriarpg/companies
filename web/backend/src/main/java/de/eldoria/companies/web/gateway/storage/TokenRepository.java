/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import de.eldoria.companies.web.gateway.Secrets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The server tokens of a self-hosted gateway, one per network. A token is shown once when it is created and stored
 * only as its hash. Its id stays when it is regenerated, so the network keeps its sessions.
 */
public final class TokenRepository {
    /**
     * The prefix of every server token, so it is recognisable in a config file.
     */
    public static final String PREFIX = "cst_";

    /**
     * A server token as stored, without the token.
     *
     * @param id the stable id, part of the server id
     * @param name the name the owner gave the network
     * @param created when the token was created
     */
    public record StoredToken(String id, String name, Instant created) {}

    /**
     * @return how many tokens exist
     */
    public int count() {
        return query("SELECT count(*) AS count FROM server_token;")
                .single()
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    /**
     * Creates a token for a network.
     *
     * @param name the network's name
     * @return the token, to be shown once; none when the name is taken
     */
    public Optional<String> create(String name) {
        String token = PREFIX + Secrets.token();
        boolean created = query("""
                INSERT INTO server_token(id, name, token_hash) VALUES (:id, :name, :hash)
                ON CONFLICT (name) DO NOTHING;""")
                .single(call().bind("id", Secrets.sessionId())
                        .bind("name", name)
                        .bind("hash", Secrets.hash(token)))
                .insert()
                .changed();
        return created ? Optional.of(token) : Optional.empty();
    }

    /**
     * Replaces the token of a network; the old token stops working at once.
     *
     * @param name the network's name
     * @return the new token, to be shown once; none for an unknown name
     */
    public Optional<String> regenerate(String name) {
        String token = PREFIX + Secrets.token();
        boolean changed = query("UPDATE server_token SET token_hash = :hash WHERE name = :name;")
                .single(call().bind("name", name).bind("hash", Secrets.hash(token)))
                .update()
                .changed();
        return changed ? Optional.of(token) : Optional.empty();
    }

    /**
     * @param name the network's name
     * @return whether a token was deleted
     */
    public boolean delete(String name) {
        return query("DELETE FROM server_token WHERE name = :name;")
                .single(call().bind("name", name))
                .delete()
                .changed();
    }

    /**
     * @return every token, without the tokens themselves
     */
    public List<StoredToken> list() {
        return query("SELECT id, name, created FROM server_token ORDER BY name;")
                .single()
                .map(row -> new StoredToken(
                        row.getString("id"),
                        row.getString("name"),
                        row.getTimestamp("created").toInstant()))
                .all();
    }

    /**
     * @param token a token as presented by a node
     * @return the token it is
     */
    public Optional<StoredToken> find(String token) {
        return query("SELECT id, name, created FROM server_token WHERE token_hash = :hash;")
                .single(call().bind("hash", Secrets.hash(token)))
                .map(row -> new StoredToken(
                        row.getString("id"),
                        row.getString("name"),
                        row.getTimestamp("created").toInstant()))
                .first();
    }
}
