/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.dashboard;

/**
 * The {@code dashboard} section of {@code node.yml}, which is never synced.
 * <p>
 * It can hand the network its first dashboard key or the token of a self-hosted gateway, usually as
 * {@code env:NAME}. The key is moved into the database, where it rotates; the configured value is read again only when
 * it changes.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class NodeDashboardSettings {
    /**
     * The value of a new {@code node.yml}: the environment variable {@code COMPANIES_DASHBOARD_KEY}.
     */
    public static final String DEFAULT_KEY = "env:COMPANIES_DASHBOARD_KEY";

    private String serverKey = DEFAULT_KEY;

    public NodeDashboardSettings() {}

    /**
     * The key as configured: the key itself, {@code env:NAME} or empty for none. Never log it.
     *
     * @return the configured key
     */
    public String serverKey() {
        return serverKey == null ? "" : serverKey;
    }
}
