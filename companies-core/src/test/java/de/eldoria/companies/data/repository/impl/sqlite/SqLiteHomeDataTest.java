/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.AHomeData;
import de.eldoria.companies.data.repository.HomeDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLiteHomeDataTest extends HomeDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-homes"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AHomeData homes() {
        return new SqLiteHomeData();
    }
}
