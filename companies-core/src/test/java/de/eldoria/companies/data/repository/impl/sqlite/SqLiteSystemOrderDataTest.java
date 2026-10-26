/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.SystemOrderDataContract;
import de.eldoria.companies.data.repository.impl.TimeColumns;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

import javax.sql.DataSource;

class SqLiteSystemOrderDataTest extends SystemOrderDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-system-orders"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AOrderData orders() {
        return new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
    }

    @Override
    protected AHistoryData history(ExecutorService executor) {
        return new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
    }

    @Override
    protected ADashboardData dashboard() {
        return new SqLiteDashboardData();
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.SQLITE_TEXT;
    }
}
