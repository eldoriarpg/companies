/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbJobData;
import org.intellij.lang.annotations.Language;

/**
 * Jobs for PostgreSQL, which runs the statements of MariaDB unchanged except for the upserts of schematics and areas.
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

    @Override
    protected String areaUpsert() {
        @Language("postgresql")
        var query = """
                INSERT INTO job_areas(owner_uuid, name, node, world, min_x, min_y, min_z, max_x, max_y, max_z, created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_uuid, name) DO UPDATE SET node = excluded.node, world = excluded.world,
                    min_x = excluded.min_x, min_y = excluded.min_y, min_z = excluded.min_z, max_x = excluded.max_x,
                    max_y = excluded.max_y, max_z = excluded.max_z, created = excluded.created""";
        return query;
    }
}
