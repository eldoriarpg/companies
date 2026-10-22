/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbHistoryData;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * The order history for PostgreSQL, whose snapshot column is {@code jsonb}.
 */
public class PostgresHistoryData extends MariaDbHistoryData {
    /**
     * @param mapper   mapper for snapshots, able to read and write item stacks
     * @param executor executor of the futures
     */
    public PostgresHistoryData(ObjectMapper mapper, ExecutorService executor) {
        super(mapper, executor);
    }

    @Override
    protected String jsonParameter() {
        return "?::jsonb";
    }
}
