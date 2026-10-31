import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default

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
    implementation(project(":web-api")) {
        exclude("*")
    }
    implementation(project(":companies-hooks")) {
        exclude("*")
    }
    implementation(libs.messageblocker)
    implementation(libs.bundles.eldoria.utilities) {
        exclude("net.kyori")
        exclude("org.jetbrains")
    }
    implementation(platform(libs.jackson.bom))
    implementation(libs.bundles.jackson)
    bukkitLibrary(libs.bundles.database)
    bukkitLibrary(libs.bundles.sadu)
    bukkitLibrary(libs.jedis)
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
    optional("Citizens") {
        link("https://citizensnpcs.co")
    }
    optional("FancyNpcs") {
        modrinth("fancynpcs")
        hangar("Oliver/FancyNpcs")
    }
    optional("WorldGuard") {
        link("https://enginehub.org/worldguard")
    }
    optional("WorldEdit") {
        modrinth("worldedit")
    }
    optional("PlotSquared") {
        link("https://www.spigotmc.org/resources/77506/")
    }
}

tasks {
    shadowJar {
        relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("de.eldoria.jacksonbukkit", shadebase + "jacksonbukkit")
        relocate("de.eldoria.messageblocker", shadebase + "messageblocker")
        relocate("dev.chojo.ocular", shadebase + "ocular")
        relocate("tools.jackson", shadebase + "jackson")
        relocate("com.fasterxml", shadebase + "fasterxml")
        relocate("org.snakeyaml", shadebase + "snakeyaml")
        mergeServiceFiles()
        addMultiReleaseAttribute = false
        exclude("META-INF/versions/**")
        exclude("META-INF/services/javax.annotation.processing.Processor")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.3")
        downloadPlugins {
            url("https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar")
            url("https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsX-2.22.0.jar")
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
    softDepend = listOf(
        "PlaceholderAPI",
        "ProtocolLib",
        "Citizens",
        "FancyNpcs",
        "WorldEdit",
        "FastAsyncWorldEdit",
        "WorldGuard",
        "PlotSquared"
    )

    permissions {
        register("companies.*") {
            description = "Gives access to all company commands"
            children = listOf(
                "companies.orders.*",
                "companies.company.*",
                "companies.boards.use",
                "companies.dashboard.use",
                "companies.admin.*",
                "companies.updates"
            )
        }

        register("companies.updates") {
            description = "Hear about new versions on join and download them"
        }

        register("companies.orders.*") {
            description = "Gives full access to orders. Does not raise the order limit, use companies.orders.limit.<n> for that"
            children = listOf("companies.orders.create", "companies.orders.repeat", "companies.orders.solo", "companies.orders.jobs")
        }
        register("companies.orders.create") {
            description = "Allows creation of orders, up to maxOrders in user.yml or the highest companies.orders.limit.<n>"
            default = Default.TRUE
        }

        register("companies.orders.repeat") {
            description = "Allows making orders repeat daily or weekly"
            default = Default.TRUE
        }

        register("companies.orders.solo") {
            description = "Allows working on orders alone, without a company, while solo orders are enabled in order.yml"
            default = Default.TRUE
        }

        register("companies.orders.jobs") {
            description = "Allows posting jobs: work in an area of the player's land that a company takes like an order"
            default = Default.TRUE
        }

        register("companies.company.*") {
            description = "Gives access to company commands"
            children = listOf("companies.company.create", "companies.company.join", "companies.company.home")
        }
        register("companies.company.create") {
            description = "Allows creation of a company"
            default = Default.TRUE
        }
        register("companies.company.join") {
            description = "Allows joining a company"
            default = Default.TRUE
        }
        register("companies.company.home") {
            description = "Allows teleporting to the home of the player's company"
            default = Default.TRUE
        }

        register("companies.boards.use") {
            description = "Allows opening order boards through signs, NPCs and /company board"
            default = Default.TRUE
        }

        register("companies.dashboard.use") {
            description = "Allows opening the web dashboard with /company dashboard while it is enabled"
            default = Default.TRUE
        }

        register("companies.admin.*") {
            description = "Gives access to all admin commands"
            children = listOf(
                "companies.admin",
                "companies.admin.boards",
                "companies.admin.calcLevel",
                "companies.admin.dashboard",
                "companies.admin.debug",
                "companies.admin.delete",
                "companies.admin.events",
                "companies.admin.grant",
                "companies.admin.jobs",
                "companies.admin.jobs.bypass",
                "companies.admin.level",
                "companies.admin.nodes",
                "companies.admin.offers",
                "companies.admin.orders",
                "companies.admin.player",
                "companies.admin.reload",
                "companies.admin.rename",
                "companies.admin.transferOwner",
                "companies.admin.treasury"
            )
        }
        register("companies.admin") {
            description = "Allows syncing the configuration between nodes"
        }
        register("companies.admin.boards") {
            description = "Allows creating and breaking board signs and managing boards"
        }
        register("companies.admin.calcLevel") {
            description = "Allows recalculating company levels"
        }
        register("companies.admin.dashboard") {
            description = "Allows showing the dashboard connection, ending a player's dashboard sessions and entering the dashboard key"
        }
        register("companies.admin.debug") {
            description = "Allows creating debug reports"
            default = Default.OP
        }
        register("companies.admin.delete") {
            description = "Allows deleting companies"
        }
        register("companies.admin.events") {
            description = "Allows creating, inspecting, ending, cancelling, extending and funding server events"
        }
        register("companies.admin.grant") {
            description = "Allows granting items and money to players"
        }
        register("companies.admin.jobs") {
            description = "Allows listing and settling disputes about jobs"
        }
        register("companies.admin.jobs.bypass") {
            description = "Lets a player build in job areas of other companies and act outside a job area reached only through a job"
        }
        register("companies.admin.level") {
            description = "Allows managing of company level settings"
        }
        register("companies.admin.nodes") {
            description = "Allows listing and removing the nodes registered in the database"
        }
        register("companies.admin.offers") {
            description = "Allows creating, inspecting, pausing, resuming and cancelling event offers"
        }
        register("companies.admin.orders") {
            description = "Allows inspecting, releasing, cancelling, completing and extending any order"
        }
        register("companies.admin.player") {
            description = "Allows looking up the company, orders, items and notifications of a player"
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
        register("companies.admin.treasury") {
            description = "Allows inspecting company treasuries and adding or removing money"
        }
    }
}
