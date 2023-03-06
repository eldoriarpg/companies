import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.0"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
}

group = "de.eldoria"
val shadebase = project.group as String + ".companies.libs."

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":companies-api"))
    implementation("de.eldoria", "eldo-util", "1.13.9")
    implementation("de.chojo.sadu", "sadu-queries", "1.2.0")
    implementation("de.chojo.sadu", "sadu-updater", "1.2.0")
    implementation("de.chojo.sadu", "sadu-datasource", "1.2.0")
    implementation("de.chojo.sadu", "sadu-postgresql", "1.2.0")
    implementation("de.chojo.sadu", "sadu-mariadb", "1.2.0")
    implementation("de.chojo.sadu", "sadu-sqlite", "1.2.0")
    // text
    implementation("net.kyori", "adventure-api", "4.12.0")
    implementation("net.kyori", "adventure-platform-bukkit", "4.2.0")

    compileOnly("com.comphenix.protocol", "ProtocolLib", "4.8.0")
    compileOnly("me.clip", "placeholderapi", "2.11.2")

    // database
    bukkitLibrary("com.zaxxer", "HikariCP", "5.0.1")
    bukkitLibrary("org.mariadb.jdbc", "mariadb-java-client", "2.7.8")
    bukkitLibrary("org.xerial", "sqlite-jdbc", "3.36.0.3")
    bukkitLibrary("org.postgresql", "postgresql", "42.2.23")

    compileOnly("com.github.MilkBowl", "VaultAPI", "1.7.1")
}

fun getBuildType(): String {
    return when {
        System.getenv("PATREON")?.equals("true", true) == true -> {
            "PATREON"
        }
        publishData.isPublicBuild() -> {
            "PUBLIC";
        }
        else -> "LOCAL"
    }
}

tasks {
    processResources {
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("build.data") {
                expand(
                    "type" to getBuildType(),
                    "time" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                    "branch" to publishData.getBranch(),
                    "commit" to publishData.getCommitHash()
                )
            }
        }

        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    shadowJar {
        relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("net.kyori", shadebase + "kyori")
        relocate("de.chojo.sqlutil", shadebase + "sqlutil")
        mergeServiceFiles()
        minimize()
        archiveClassifier.set("")
        archiveBaseName.set("Companies")
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    register<Copy>("copyToServer") {
        val path = project.property("targetDir") ?: ""
        if (path.toString().isEmpty()) {
            println("targetDir is not set in gradle properties")
            return@register
        }
        from(shadowJar)
        destinationDir = File(path.toString())
    }
}

bukkit {
    name = "Companies"
    main = "de.eldoria.companies.Companies"
    version = publishData.getVersion(true)
    description = "Order and deliver items"
    apiVersion = "1.16"
    version = publishData.getVersion(true)
    authors = listOf("RainbowDashLabs")
    depend = listOf("Vault")
    softDepend = listOf("PlaceholderAPI", "ProtocolLib")

    commands {
        register("company") {
            description = "Manage your company"
            aliases = listOf("comp")
        }

        register("order") {
            description = "Manage your orders"
        }

        register("companyadmin") {
            description = "Manage the plugin"
        }
    }

    permissions {
        register("companies.*") {
            description = "gives access to all company commands"
            children = listOf(
                "companies.order.*",
                "companies.admin.*",
                "companies.company.*"
            )
        }

        register("companies.order.*") {
            description = "Gives full access to orders. Will not override the order limit"
            children = listOf("companies.order.create")
        }

        register("companies.admin.*") {
            description = "Hives access to admin commands"
            children = listOf("companies.admin.level")
        }

        register("companies.company.*") {
            description = "Gives access to company commands"
            children = listOf("companies.company.create")
        }

        register("companies.order.create") {
            description = "Allows creation of orders"
        }
        register("companies.admin.level") {
            description = "Allows managing of company level settings"
        }
        register("companies.company.create") {
            description = "Allows creation of a company"
        }
    }
}
