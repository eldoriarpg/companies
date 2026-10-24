/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * One PostgreSQL 17 container for every test class; each class uses its own schema.
 */
final class TestDatabase {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private TestDatabase() {}

    /**
     * @param schema the schema of the test class
     * @return the environment of a gateway on that schema, with test defaults
     */
    static synchronized Map<String, String> env(String schema) {
        if (!POSTGRES.isRunning()) POSTGRES.start();
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", POSTGRES.getHost());
        env.put("DB_PORT", Integer.toString(POSTGRES.getFirstMappedPort()));
        env.put("DB_NAME", POSTGRES.getDatabaseName());
        env.put("DB_USER", POSTGRES.getUsername());
        env.put("DB_PASSWORD", POSTGRES.getPassword());
        env.put("DB_SCHEMA", schema);
        env.put("PUBLIC_URL", "https://dash.test");
        env.put("TRUSTED_PROXIES", "127.0.0.1,::1");
        env.put("CONNECTS_PER_ADDRESS", "1000");
        env.put("REQUEST_TIMEOUT_SECONDS", "5");
        return env;
    }
}
