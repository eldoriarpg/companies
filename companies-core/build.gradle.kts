plugins {
    id("com.github.johnrengelman.shadow") version "8.1.0"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
}

group = "de.eldoria"
val shadebase = project.group as String + ".companies.libs."

repositories {
    mavenCentral()
}

publishData {
    addBuildData()
    useEldoNexusRepos()
    publishTask("shadowJar")
    publishTask("javadocJar")
    publishTask("sourcesJar")
}

publishing {
    publications.create<MavenPublication>("maven") {
        publishData.configurePublication(this)
    }

    repositories {
        maven {
            authentication {
                credentials(PasswordCredentials::class) {
                    username = System.getenv("NEXUS_USERNAME")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }

            setUrl(publishData.getRepository())
            name = "EldoNexus"
        }
    }
}

dependencies {
    implementation(project(":companies-api"))
    implementation("de.chojo.sadu", "sadu-queries", "1.3.0")
    implementation("de.chojo.sadu", "sadu-updater", "1.3.0")
    implementation("de.chojo.sadu", "sadu-datasource", "1.3.0")
    implementation("de.chojo.sadu", "sadu-postgresql", "1.3.0")
    implementation("de.chojo.sadu", "sadu-mariadb", "1.3.0")
    implementation("de.chojo.sadu", "sadu-sqlite", "1.3.0")
    // text
    bukkitLibrary("net.kyori", "adventure-api", "4.12.0")
    bukkitLibrary("net.kyori", "adventure-platform-bukkit", "4.2.0")

    compileOnly("com.comphenix.protocol", "ProtocolLib", "4.8.0")
    compileOnly("me.clip", "placeholderapi", "2.11.2")

    // database
    bukkitLibrary("com.zaxxer", "HikariCP", "5.0.1")
    bukkitLibrary("org.mariadb.jdbc", "mariadb-java-client", "3.1.2")
    bukkitLibrary("org.xerial", "sqlite-jdbc", "3.41.0.0")
    bukkitLibrary("org.postgresql", "postgresql", "42.5.4")

    compileOnly("com.github.MilkBowl", "VaultAPI", "1.7.1")
}

tasks {
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
