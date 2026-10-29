/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.OfferDataContract;

import java.nio.file.Files;

import javax.sql.DataSource;

class SqLiteOfferDataTest extends OfferDataContract {
    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(Files.createTempDirectory("companies-offers"));
    }

    @Override
    protected void stop() {}

    @Override
    protected AOrderData orders() {
        return new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
    }

    @Override
    protected AOfferData offers() {
        return new SqLiteOfferData(SqLiteTestDatabase.mapper());
    }
}
