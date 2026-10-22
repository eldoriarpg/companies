plugins {
    id("companies.publishing-conventions")
    id("de.chojo.publishdata.manifest")
    alias(libs.plugins.pluginyml.bukkit)
    alias(libs.plugins.shadow)
    alias(libs.plugins.runserver)
}

dependencies {
    implementation(project(":companies-core")) {
        exclude("*")
    }
    implementation(project(":companies-api"))
    implementation(libs.messageblocker)
    implementation(libs.bundles.eldoria.utilities)
    implementation(libs.bundles.adventure)
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

tasks {
    shadowJar {
        val shadebase = "de.eldoria.companies.libs."
        relocate("net.kyori", shadebase + "kyori")
        relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("de.eldoria.jacksonbukkit", shadebase + "jacksonbukkit")
        relocate("de.eldoria.messageblocker", shadebase + "messageblocker")
        relocate("com.fasterxml", shadebase + "fasterxml")
        relocate("org.yaml", shadebase + "yaml")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }


    runServer {
        minecraftVersion("1.21.4")
        downloadPlugins {
            url("https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar")
            url("https://download.luckperms.net/1569/bukkit/loader/LuckPerms-Bukkit-5.4.152.jar")
        }

        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}


bukkit {
    name = "Companies"
    main = "de.eldoria.companies.Companies"
    description = "Order and deliver items"
    apiVersion = "1.16"
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
