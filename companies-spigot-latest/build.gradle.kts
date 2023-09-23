plugins {
    alias(libs.plugins.pluginyml.bukkit)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":companies-core")) {
        exclude("com.zaxxer")
        exclude("com.google.code")
        exclude("org.jetbrains")
        exclude("org.slf4j")
    }

    bukkitLibrary(libs.bundles.adventure)
    bukkitLibrary(libs.bundles.jackson)
    bukkitLibrary(libs.bundles.database)
    bukkitLibrary(libs.bundles.sadu)
    bukkitLibrary(libs.bundles.eldoria.utilities)
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

tasks {
    shadowJar {
        val shadebase = "de.eldoria.companies.libs."
        relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("de.eldoria.messageblocker", shadebase + "messageblocker")
        relocate("de.eldoria.jacksonbukkit", shadebase + "jacksonbukkit")
        relocate("de.chojo", shadebase + "chojo")
        mergeServiceFiles()
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

    build {
        dependsOn(shadowJar)
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

