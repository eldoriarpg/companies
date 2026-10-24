/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbDashboardData;
import org.intellij.lang.annotations.Language;

/**
 * The dashboard tables for PostgreSQL, which upserts with {@code ON CONFLICT}.
 */
public class PostgresDashboardData extends MariaDbDashboardData {
    @Override
    @Language("postgresql")
    protected String upsertKey() {
        return """
                INSERT INTO dashboard_key(id, secret, kind, refresh_at, checked_at, seed_hash, updated)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET secret     = excluded.secret,
                                               kind       = excluded.kind,
                                               refresh_at = excluded.refresh_at,
                                               checked_at = excluded.checked_at,
                                               seed_hash  = excluded.seed_hash,
                                               updated    = excluded.updated""";
    }
}
