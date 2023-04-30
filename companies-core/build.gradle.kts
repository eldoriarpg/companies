plugins {
    java
    `java-library`
}

dependencies {
    api(project(":companies-api"))
    api("de.chojo.sadu", "sadu-queries", "1.3.0")
    api("de.chojo.sadu", "sadu-updater", "1.3.0")
    api("de.chojo.sadu", "sadu-datasource", "1.3.0")
    api("de.chojo.sadu", "sadu-postgresql", "1.3.0")
    api("de.chojo.sadu", "sadu-mariadb", "1.3.0")
    api("de.chojo.sadu", "sadu-sqlite", "1.3.0")

    api("de.eldoria.util", "jackson-configuration", "2.0.0-DEV") {
        exclude("com.fasterxml.jackson.dataformat")
        exclude("com.fasterxml.jackson.core")
        exclude("com.fasterxml.jackson")
    }
    api("de.eldoria.util", "plugin", "2.0.0-DEV"){
        exclude("net.kyori")
    }
    api("de.eldoria.util", "threading", "2.0.0-DEV")
    api("de.eldoria", "messageblocker", "1.1.2")

    compileOnly("com.comphenix.protocol", "ProtocolLib", "4.8.0")
    compileOnly("me.clip", "placeholderapi", "2.11.3")
    compileOnly("com.github.MilkBowl", "VaultAPI", "1.7.1")

    compileOnly("net.kyori", "adventure-api", "4.12.0")
    compileOnly("net.kyori", "adventure-platform-bukkit", "4.2.0")

    compileOnly("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", "2.14.2")
    compileOnly("com.fasterxml.jackson.core", "jackson-core", "2.14.2")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.14.2")

    // database
    compileOnly("com.zaxxer", "HikariCP", "5.0.1")
    compileOnly("org.mariadb.jdbc", "mariadb-java-client", "3.1.2")
    compileOnly("org.xerial", "sqlite-jdbc", "3.41.2.1")
    compileOnly("org.postgresql", "postgresql", "42.5.4")

    testImplementation("net.kyori", "adventure-api", "4.12.0")
    testImplementation("net.kyori", "adventure-platform-bukkit", "4.2.0")
}
