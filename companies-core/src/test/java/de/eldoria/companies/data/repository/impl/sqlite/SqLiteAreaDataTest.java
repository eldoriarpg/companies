/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.repository.AreaDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLiteAreaDataTest extends AreaDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-areas"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AAreaData areas() {
        return new SqLiteAreaData();
    }
}
