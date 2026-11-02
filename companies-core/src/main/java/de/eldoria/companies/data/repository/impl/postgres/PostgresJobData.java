/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbJobData;
import org.intellij.lang.annotations.Language;

/**
 * Jobs for PostgreSQL, which runs the statements of MariaDB unchanged except for the upsert of schematics.
 */
public class PostgresJobData extends MariaDbJobData {
    @Override
    protected String schematicUpsert() {
        @Language("postgresql")
        var query = """
                INSERT INTO job_schematics(owner_uuid, name, data, created)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (owner_uuid, name) DO UPDATE SET data = excluded.data, created = excluded.created""";
        return query;
    }
}
