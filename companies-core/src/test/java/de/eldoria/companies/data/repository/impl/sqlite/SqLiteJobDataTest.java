/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.repository.JobDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLiteJobDataTest extends JobDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-jobs"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AJobData jobs() {
        return new SqLiteJobData();
    }
}
