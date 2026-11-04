import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default

plugins {
    id("companies.java-conventions")
    alias(libs.plugins.pluginyml.bukkit)
}

dependencies {
    compileOnly(project(":companies-api"))
    compileOnly(libs.mariadb)
    compileOnly(libs.luckperms)
    bukkitLibrary(libs.mariadb)
    testImplementation(project(":companies-api"))
    testImplementation(libs.luckperms)
}

bukkit {
    name = "CompaniesDemo"
    main = "de.eldoria.companies.demo.CompaniesDemo"
    description = "Starting state of the Companies demo network"
    apiVersion = "1.21.11"
    version = project.version.toString()
    authors = listOf("RainbowDashLabs")
    depend = listOf("Companies", "LuckPerms")
    permissions {
        register("companiesdemo.commands.all") {
            description = "Use every command, not only those of the demo's allow list"
            default = Default.OP
        }
        register("companiesdemo.pay.unlimited") {
            description = "Pay any amount with /pay, not only up to pay.maxAmount"
            default = Default.OP
        }
        register("companiesdemo.admin") {
            description = "Switch the admin sandbox on with /demo admin"
            default = Default.TRUE
        }
    }
}
