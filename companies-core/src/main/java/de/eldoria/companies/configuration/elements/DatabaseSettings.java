/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor", "CanBeFinal"})
public class DatabaseSettings {
    private StorageType storageType = StorageType.SQLITE;
    private String host = "localhost";
    private String port = "3306";
    private String user = "root";
    private String password = "pass";
    private String database = "public";
    private String schema = "public";
    private RedisSettings redis = new RedisSettings();
    private MessagingSettings messaging = new MessagingSettings();

    public DatabaseSettings() {}

    public StorageType storageType() {
        return storageType;
    }

    public String host() {
        return host;
    }

    public String port() {
        return port;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String database() {
        return database;
    }

    public String schema() {
        return schema;
    }

    /**
     * @return the Redis server the nodes send each other messages through
     */
    public RedisSettings redis() {
        return redis == null ? new RedisSettings() : redis;
    }

    /**
     * @return how the nodes exchange messages without Redis
     */
    public MessagingSettings messaging() {
        return messaging == null ? new MessagingSettings() : messaging;
    }

    /**
     * The prefix of every channel of the node bus, for Redis and PostgreSQL notify alike. It is
     * {@code messaging.channelPrefix}, unless that is left at its default and the older {@code redis.channelPrefix}
     * is set, so files written before the key moved keep their prefix.
     *
     * @return the prefix
     */
    public String channelPrefix() {
        var prefix = messaging().channelPrefix();
        var older = redis().channelPrefix();
        if (prefix.equals(MessagingSettings.DEFAULT_CHANNEL_PREFIX) && !older.isEmpty()) return older;
        return prefix;
    }

    public enum StorageType {
        SQLITE,
        MARIADB,
        POSTGRES
    }
}
