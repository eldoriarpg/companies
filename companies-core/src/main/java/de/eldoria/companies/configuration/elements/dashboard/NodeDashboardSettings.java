/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * The {@code dashboard} section of {@code node.yml}, which is never synced.
 * <p>
 * It can hand the network its first dashboard key or the token of a self-hosted gateway, usually through the
 * environment variable {@code COMPANIES_DASHBOARD_KEY}, which is kept apart from the file's value and never written.
 * The key is moved into the database, where it rotates; the configured value is read again only when it changes.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class NodeDashboardSettings {
    private String serverKey = "";

    @JsonIgnore
    @Overwrite(env = @Env("COMPANIES_DASHBOARD_KEY"))
    private String serverKeyOverride;

    public NodeDashboardSettings() {}

    /**
     * The key: the environment variable {@code COMPANIES_DASHBOARD_KEY} where it is set, otherwise the file's, empty for
     * none. Never log it.
     *
     * @return the key
     */
    public String serverKey() {
        if (serverKeyOverride != null && !serverKeyOverride.isBlank()) return serverKeyOverride.trim();
        return serverKey == null ? "" : serverKey.trim();
    }
}
