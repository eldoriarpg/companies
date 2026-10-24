/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

/**
 * How the nodes of a network exchange messages, the {@code messaging} section of {@code database.yml}. Read at start
 * only and never synced. All nodes of a network should use the same settings.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class MessagingSettings {
    /**
     * The channel prefix of a file that sets none.
     */
    public static final String DEFAULT_CHANNEL_PREFIX = "companies";

    private boolean lowLatency = false;
    private boolean postgresNotify = false;
    private String channelPrefix = DEFAULT_CHANNEL_PREFIX;

    public MessagingSettings() {}

    /**
     * Whether the nodes exchange their messages through a table in the database, which every node reads every
     * second. Ignored while Redis or PostgreSQL notify is used.
     *
     * @return true when low latency mode is on
     */
    public boolean lowLatency() {
        return lowLatency;
    }

    /**
     * Whether the nodes exchange their messages with {@code LISTEN} and {@code NOTIFY} of PostgreSQL. Only used with
     * the storage type {@code POSTGRES}, and ignored while Redis is enabled.
     *
     * @return true when PostgreSQL notify is on
     */
    public boolean postgresNotify() {
        return postgresNotify;
    }

    /**
     * The prefix of every channel as written in this section, so several networks can share one Redis server or one
     * PostgreSQL database. {@link DatabaseSettings#channelPrefix()} decides between it and the older
     * {@code redis.channelPrefix}.
     *
     * @return the prefix, {@value #DEFAULT_CHANNEL_PREFIX} when none is set
     */
    public String channelPrefix() {
        return channelPrefix == null || channelPrefix.isBlank() ? DEFAULT_CHANNEL_PREFIX : channelPrefix.trim();
    }
}
