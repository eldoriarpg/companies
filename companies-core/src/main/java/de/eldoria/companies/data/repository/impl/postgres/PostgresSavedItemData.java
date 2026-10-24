/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbSavedItemData;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * The saved items for PostgreSQL, whose queries are those of MariaDB.
 */
public class PostgresSavedItemData extends MariaDbSavedItemData {
    public PostgresSavedItemData(ObjectMapper mapper, ExecutorService executor) {
        super(mapper, executor);
    }
}
