/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What the gateway remembers about itself across restarts: when it last ran, so the time it was down can be told.
 */
public final class GatewayStateRepository {
    /**
     * @return when the gateway was last known to run, none before the first run
     */
    public Optional<Instant> aliveAt() {
        return query("SELECT alive_at FROM gateway_state WHERE id = 1;")
                .single()
                .map(row -> row.getTimestamp("alive_at").toInstant())
                .first();
    }

    /**
     * Notes that the gateway runs.
     *
     * @param now the time
     */
    public void alive(Instant now) {
        query("""
                INSERT INTO gateway_state(id, alive_at) VALUES (1, :now)
                ON CONFLICT (id) DO UPDATE SET alive_at = :now;""").single(call().bind("now", Timestamp.from(now))).insert();
    }
}
