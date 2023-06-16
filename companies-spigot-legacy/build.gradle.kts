plugins {
    id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":companies-core")) {
        exclude("com.zaxxer")
        exclude("com.zaxxer")
        exclude("com.google.code")
        exclude("org.jetbrains")
        exclude("org.slf4j")
    }

    // text
    implementation("net.kyori", "adventure-api", "4.12.0")
    implementation("net.kyori", "adventure-platform-bukkit", "4.2.0")

    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", "2.15.2")
    implementation("com.fasterxml.jackson.core", "jackson-core", "2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")

    // database
    bukkitLibrary("com.zaxxer", "HikariCP", "5.0.1")
    bukkitLibrary("org.mariadb.jdbc", "mariadb-java-client", "3.1.4")
    bukkitLibrary("org.xerial", "sqlite-jdbc", "3.41.2.1")
    bukkitLibrary("org.postgresql", "postgresql", "42.5.4")
}


tasks {
    shadowJar {
        val shadebase = "de.eldoria.companies.libs."
        relocate("net.kyori", shadebase + "kyori")
        relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("de.eldoria.jacksonbukkit", shadebase + "jacksonbukkit")
        relocate("de.eldoria.messageblocker", shadebase + "messageblocker")
        relocate("com.fasterxml", shadebase + "fasterxml")
        relocate("org.yaml", shadebase + "yaml")
        relocate("de.chojo", shadebase + "chojo")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}

publishData {
    addBuildData()
    useInternalEldoNexusRepos()
    publishTask("shadowJar")
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
