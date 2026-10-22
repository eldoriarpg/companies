plugins {
    id("companies.publishing-conventions")
    id("de.chojo.publishdata.manifest")
    alias(libs.plugins.pluginyml.bukkit)
    alias(libs.plugins.shadow)
    alias(libs.plugins.runserver)
}

val shadebase = "de.eldoria.companies.libs."

dependencies {
    implementation(project(":companies-core")) {
        exclude("*")
    }
    implementation(project(":companies-api"))
    implementation(libs.messageblocker)
    implementation(libs.bundles.eldoria.utilities) {
        exclude("net.kyori")
    }
    implementation(libs.bundles.jackson)
    bukkitLibrary(libs.bundles.database)
    bukkitLibrary(libs.bundles.sadu)
}

publishData {
    eldoNexus(internal = true, dev = false)
    component = "shadow"
    buildData {
        put("lynaProduct", "2")
    }
}

pluginManifest {
    platforms("paper")
    requires("Vault") {
        link("https://github.com/MilkBowl/Vault")
    }
    optional("PlaceholderAPI") {
        modrinth("placeholderapi")
        hangar("HelpChat/PlaceholderAPI")
    }
    optional("ProtocolLib") {
        link("https://github.com/dmulloy2/ProtocolLib")
    }
}

tasks {
    shadowJar {
        relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("de.eldoria.jacksonbukkit", shadebase + "jacksonbukkit")
        relocate("de.eldoria.messageblocker", shadebase + "messageblocker")
        relocate("com.fasterxml", shadebase + "fasterxml")
        relocate("org.yaml", shadebase + "yaml")
        mergeServiceFiles()
        addMultiReleaseAttribute = false
        exclude("META-INF/versions/**")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.3")
        downloadPlugins {
            url("https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar")
            modrinth("luckperms", "v5.5.71-bukkit")
        }

        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}

bukkit {
    name = "Companies"
    main = "de.eldoria.companies.Companies"
    description = "Order and deliver items"
    apiVersion = "1.21.11"
    version = project.version.toString()
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
            description = "Gives access to all company commands"
            children = listOf(
                "companies.orders.*",
                "companies.company.*",
                "companies.admin.*"
            )
        }

        register("companies.orders.*") {
            description = "Gives full access to orders. Does not override the order limit"
            children = listOf("companies.orders.create")
        }
        register("companies.orders.create") {
            description = "Allows creation of orders"
        }

        register("companies.company.*") {
            description = "Gives access to company commands"
            children = listOf("companies.company.create", "companies.company.join")
        }
        register("companies.company.create") {
            description = "Allows creation of a company"
        }
        register("companies.company.join") {
            description = "Allows joining a company"
        }

        register("companies.admin.*") {
            description = "Gives access to all admin commands"
            children = listOf(
                "companies.admin",
                "companies.admin.calcLevel",
                "companies.admin.delete",
                "companies.admin.level",
                "companies.admin.reload",
                "companies.admin.rename",
                "companies.admin.transferOwner"
            )
        }
        register("companies.admin") {
            description = "Allows using the admin command and receiving update notices"
        }
        register("companies.admin.calcLevel") {
            description = "Allows recalculating company levels"
        }
        register("companies.admin.delete") {
            description = "Allows deleting companies"
        }
        register("companies.admin.level") {
            description = "Allows managing of company level settings"
        }
        register("companies.admin.reload") {
            description = "Allows reloading the plugin"
        }
        register("companies.admin.rename") {
            description = "Allows renaming companies"
        }
        register("companies.admin.transferOwner") {
            description = "Allows transferring company ownership"
        }
    }
}
