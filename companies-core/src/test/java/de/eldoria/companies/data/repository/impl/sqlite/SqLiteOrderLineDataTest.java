/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.OrderLineDataContract;
import de.eldoria.companies.data.repository.impl.TimeColumns;

import java.nio.file.Files;
import java.time.LocalDateTime;

import javax.sql.DataSource;

class SqLiteOrderLineDataTest extends OrderLineDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-order-lines"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AOrderData orders() {
        return new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
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
