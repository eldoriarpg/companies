/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbStatsData;

import java.util.concurrent.ExecutorService;

/**
 * Payout ledger, ranking and member statistics for PostgreSQL, which runs the statements of MariaDB unchanged.
 */
public class PostgresStatsData extends MariaDbStatsData {
    public PostgresStatsData(ExecutorService executor) {
        super(executor);
    }
}
