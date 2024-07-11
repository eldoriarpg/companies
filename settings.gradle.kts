rootProject.name = "Companies"
include(":companies-core")
include(":companies-api")
include(":companies-spigot-latest")
include(":companies-spigot-legacy")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            name = "EldoNexus"
            url = uri("https://eldonexus.de/repository/maven-public/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // jackson & serialization
            version("jackson", "2.17.2")
            library("jackson-core", "com.fasterxml.jackson.core","jackson-core").versionRef("jackson")
            library("jackson-databind", "com.fasterxml.jackson.core","jackson-databind").versionRef("jackson")
            library("jackson-annotations", "com.fasterxml.jackson.core","jackson-annotations").versionRef("jackson")
            library("jackson-yaml", "com.fasterxml.jackson.dataformat","jackson-dataformat-yaml").versionRef("jackson")
            library("snakeyaml", "org.yaml:snakeyaml:2.2")
            bundle("jackson", listOf("jackson-databind", "jackson-annotations", "jackson-yaml"))

            // utilities
            library("messageblocker", "de.eldoria:messageblocker:1.1.2")
            version("eldoutil", "2.0.11")
            library("jackson-configuration", "de.eldoria.util","jackson-configuration").versionRef("eldoutil")
            library("plugin", "de.eldoria.util","plugin").versionRef("eldoutil")
            library("threading", "de.eldoria.util","threading").versionRef("eldoutil")
            library("updater", "de.eldoria.util","updater").versionRef("eldoutil")
            bundle("eldoria-utilities", listOf("jackson-configuration", "plugin", "threading", "updater"))

            // external dependencies
            library("protocollib","com.comphenix.protocol:ProtocolLib:5.1.0")
            library("papi","me.clip:placeholderapi:2.11.6")
            library("vault","com.github.MilkBowl:VaultAPI:1.7.1")


            // adventure
            library("adventure-bukkit", "net.kyori:adventure-platform-bukkit:4.3.3")
            library("adventure-minimessage", "net.kyori:adventure-text-minimessage:4.17.0")
            bundle("adventure", listOf("adventure-bukkit", "adventure-minimessage"))

            // database
            library("hikari", "com.zaxxer:HikariCP:5.1.0")
            library("mariadb", "org.mariadb.jdbc:mariadb-java-client:3.4.0")
            library("sqlite", "org.xerial:sqlite-jdbc:3.46.0.0")
            library("postgres", "org.postgresql:postgresql:42.7.3")
            bundle("database", listOf("hikari", "mariadb", "sqlite", "postgres"))
            version("sadu", "2.2.1")
            library("sadu-queries", "de.chojo.sadu","sadu-queries").versionRef("sadu")
            library("sadu-updater", "de.chojo.sadu","sadu-updater").versionRef("sadu")
            library("sadu-datasource", "de.chojo.sadu","sadu-datasource").versionRef("sadu")
            library("sadu-postgresql", "de.chojo.sadu","sadu-postgresql").versionRef("sadu")
            library("sadu-mariadb", "de.chojo.sadu","sadu-mariadb").versionRef("sadu")
            library("sadu-sqlite", "de.chojo.sadu","sadu-sqlite").versionRef("sadu")
            bundle("sadu", listOf("sadu-queries", "sadu-updater", "sadu-datasource", "sadu-postgresql", "sadu-mariadb", "sadu-sqlite"))


            // misc
            library("jetbrains-annotations", "org.jetbrains:annotations:24.1.0")
            // minecraft
            version("minecraft-latest", "1.20.1-R0.1-SNAPSHOT")
            library("paper-latest", "io.papermc.paper", "paper-api").versionRef("minecraft-latest")
            library("spigot-latest", "org.spigotmc", "spigot-api").versionRef("minecraft-latest")
            bundle("minecraft-latest", listOf("paper-latest", "spigot-latest"))

            library("paper-v17", "io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
            library("spigot-v16", "org.spigotmc:spigot-api:1.16.1-R0.1-SNAPSHOT")

            // world edit
            library("worldedit", "com.sk89q.worldedit:worldedit-bukkit:7.3.4")
            version("fawe", "2.10.0")
            library("fawe-core", "com.fastasyncworldedit","FastAsyncWorldEdit-Core").versionRef("fawe")
            library("fawe-bukkit", "com.fastasyncworldedit","FastAsyncWorldEdit-Bukkit").versionRef("fawe")

            // plugins
            plugin("publishdata", "de.chojo.publishdata").version("1.2.5")
            plugin("spotless", "com.diffplug.spotless").version("6.25.0")
            plugin("shadow", "io.github.goooler.shadow").version("8.1.7")
            plugin("pluginyml-bukkit", "net.minecrell.plugin-yml.bukkit").version("0.6.0")
        }

        create("testlibs") {
            library("mockbukkit", "com.github.seeseemelk:MockBukkit-v1.19:3.1.0")
        }
    }
}
