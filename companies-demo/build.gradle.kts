plugins {
    id("companies.java-conventions")
    alias(libs.plugins.pluginyml.bukkit)
}

dependencies {
    compileOnly(project(":companies-api"))
    compileOnly(libs.mariadb)
    bukkitLibrary(libs.mariadb)
    testImplementation(project(":companies-api"))
}

bukkit {
    name = "CompaniesDemo"
    main = "de.eldoria.companies.demo.CompaniesDemo"
    description = "Starting state of the Companies demo network"
    apiVersion = "1.21.11"
    version = project.version.toString()
    authors = listOf("RainbowDashLabs")
    depend = listOf("Companies")
}
