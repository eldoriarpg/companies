/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.repository.ServerEventDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLiteServerEventDataTest extends ServerEventDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-server-events"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AServerEventData events() {
        return new SqLiteServerEventData();
    }
}
