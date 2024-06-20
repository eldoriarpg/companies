/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;

import java.util.concurrent.ExecutorService;

public class PostgresNotificationData extends MariaDbNotificationData {
    /**
     * Create a new QueryFactoryholder
     *
     * @param executorService executor for futures
     */
    public PostgresNotificationData(ExecutorService executorService) {
        super(executorService);
    }
}
