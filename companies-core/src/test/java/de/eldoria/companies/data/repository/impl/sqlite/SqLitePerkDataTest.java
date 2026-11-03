/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.APerkData;
import de.eldoria.companies.data.repository.PerkDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLitePerkDataTest extends PerkDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-perks"));
    }

    @Override
    protected void stop() {}

    @Override
    protected APerkData perks() {
        return new SqLitePerkData();
    }
}
