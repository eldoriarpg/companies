/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.DashboardDataContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import javax.sql.DataSource;

class SqLiteDashboardDataTest extends DashboardDataContract {
    @TempDir
    static Path directory;

    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(directory);
    }

    @Override
    protected void stop() {}

    @Override
    protected ADashboardData data() {
        return new SqLiteDashboardData();
    }
}
