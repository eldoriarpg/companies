/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

/**
 * How the nodes of a network exchange messages without Redis, the {@code messaging} section of {@code database.yml}.
 * Read at start only and never synced. All nodes of a network should use the same settings.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class MessagingSettings {
    private boolean lowLatency = false;

    public MessagingSettings() {}

    /**
     * Whether the nodes exchange their messages through a table in the database, which every node reads every
     * second. Ignored while Redis is enabled.
     *
     * @return true when low latency mode is on
     */
    public boolean lowLatency() {
        return lowLatency;
    }
}
