rootProject.name = "Companies"
include(":companies-core")
include(":companies-api")
include(":companies-spigot-latest")
include(":companies-spigot-legacy")

pluginManagement{
    repositories{
        mavenLocal()
        gradlePluginPortal()
        maven{
            name = "EldoNexus"
            url = uri("https://eldonexus.de/repository/maven-public/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.5.0")
}
