/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * The optional Redis (or Valkey, KeyDB) server the nodes of a network send each other messages through, the
 * {@code redis} section of {@code database.yml}. Read at start only and never synced.
 * <p>
 * The password can come from the environment variable {@code COMPANIES_REDIS_PASSWORD} instead of the file; it is kept
 * apart from the file's value and never written. All nodes of a network use the same server, database and channel
 * prefix. The channel prefix moved to {@code messaging.channelPrefix};
 * {@code channelPrefix} in this section is still read from files that set it, and not written into new files.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class RedisSettings {
    private static final int DEFAULT_PORT = 6379;
    private static final int MAX_PORT = 65535;

    private boolean enabled = false;
    private String host = "localhost";
    private int port = DEFAULT_PORT;
    private String user = "";
    private String password = "";

    @JsonIgnore
    @Overwrite(env = @Env("COMPANIES_REDIS_PASSWORD"))
    private String passwordOverride;

    private boolean ssl = false;
    private int database = 0;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String channelPrefix = "";

    public RedisSettings() {}

    /**
     * @return true when the nodes send each other messages over Redis
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return address of the server
     */
    public String host() {
        return host == null || host.isBlank() ? "localhost" : host.trim();
    }

    /**
     * @return port of the server, {@code 6379} when the configured one is no valid port
     */
    public int port() {
        return port < 1 || port > MAX_PORT ? DEFAULT_PORT : port;
    }

    /**
     * @return the user for servers with access control lists, empty for the default user
     */
    public String user() {
        return user == null ? "" : user.trim();
    }

    /**
     * The password: the environment variable {@code COMPANIES_REDIS_PASSWORD} where it is set, otherwise the file's,
     * empty for none. Never log it.
     *
     * @return the password
     */
    public String password() {
        if (passwordOverride != null && !passwordOverride.isEmpty()) return passwordOverride;
        return password == null ? "" : password;
    }

    /**
     * @return true to connect with TLS
     */
    public boolean ssl() {
        return ssl;
    }

    /**
     * @return number of the Redis database, at least 0
     */
    public int database() {
        return Math.max(0, database);
    }

    /**
     * The channel prefix in its older place, see {@link DatabaseSettings#channelPrefix()}.
     *
     * @return the prefix, empty when none is set here
     */
    public String channelPrefix() {
        return channelPrefix == null ? "" : channelPrefix.trim();
    }
}
