/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.APriceData;
import de.eldoria.companies.data.repository.PriceDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLitePriceDataTest extends PriceDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-prices"));
    }

    @Override
    protected void stop() {}

    @Override
    protected APriceData prices() {
        return new SqLitePriceData();
    }

    @Override
    protected AOrderData orders() {
        return new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
    }
}
