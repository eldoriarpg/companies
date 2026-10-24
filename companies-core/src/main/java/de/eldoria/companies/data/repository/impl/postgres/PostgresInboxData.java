/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbInboxData;
import org.intellij.lang.annotations.Language;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * The inbox for PostgreSQL, whose arguments column is {@code jsonb}.
 */
public class PostgresInboxData extends MariaDbInboxData {
    public PostgresInboxData(ExecutorService executor) {
        super(executor);
    }

    @Override
    protected String jsonParameter() {
        return "?::jsonb";
    }

    @Override
    protected boolean saveMuted(UUID player, long mask) {
        @Language("postgresql")
        var query = """
                INSERT INTO notification_settings(user_uuid, muted)
                VALUES (?, ?)
                ON CONFLICT (user_uuid) DO UPDATE SET muted = excluded.muted""";
        return upsertMuted(query, player, mask);
    }
}
