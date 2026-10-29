/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOfferData;
import tools.jackson.databind.ObjectMapper;

/**
 * Event offers for PostgreSQL, which runs the statements of MariaDB unchanged.
 */
public class PostgresOfferData extends MariaDbOfferData {
    /**
     * @param mapper mapper for the exact items of offers, able to read and write item stacks
     */
    public PostgresOfferData(ObjectMapper mapper) {
        super(mapper);
    }
}
