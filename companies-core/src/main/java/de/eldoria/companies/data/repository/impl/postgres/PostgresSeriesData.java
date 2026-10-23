/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbSeriesData;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * Repeating orders for PostgreSQL, which runs the statements of MariaDB unchanged.
 */
public class PostgresSeriesData extends MariaDbSeriesData {
    public PostgresSeriesData(ObjectMapper mapper, ExecutorService executor) {
        super(mapper, executor);
    }
}
