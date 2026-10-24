/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Server icons by the SHA-256 of their PNG bytes. Servers with the same icon share one entry.
 */
public final class IconRepository {

    /**
     * @param hash the icon's hash
     * @return whether the icon is stored
     */
    public boolean exists(String hash) {
        return query("SELECT 1 FROM icon WHERE hash = :hash;")
                .single(call().bind("hash", hash))
                .map(row -> true)
                .first()
                .isPresent();
    }

    /**
     * @param hash the icon's hash
     * @param png the PNG bytes
     */
    public void store(String hash, byte[] png) {
        query("INSERT INTO icon(hash, png) VALUES (:hash, :png) ON CONFLICT (hash) DO NOTHING;")
                .single(call().bind("hash", hash).bind("png", png))
                .insert();
    }

    /**
     * @param hash the icon's hash
     * @return the PNG bytes
     */
    public Optional<byte[]> find(String hash) {
        return query("SELECT png FROM icon WHERE hash = :hash;")
                .single(call().bind("hash", hash))
                .map(row -> row.getBytes("png"))
                .first();
    }
}
