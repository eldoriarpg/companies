/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.eldoutilities.serialization.SerializationUtil;
import de.eldoria.eldoutilities.utils.EnumUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DatabaseSettings {
    private StorageType storageType = StorageType.SQLITE;
    private String host = "localhost";
    private String port = "3306";
    private String user = "root";
    private String password = "pass";
    private String database = "public";
    private String schema = "public";

    public DatabaseSettings() {
    }

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

    public enum StorageType {
        SQLITE, MARIADB, POSTGRES
    }
}
