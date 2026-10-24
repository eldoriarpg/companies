rootProject.name = "Companies"
include(":companies-core")
include(":companies-api")
include(":companies-paper")
include(":companies-hooks")
include(":web-api")
include(":json-schema")
project(":web-api").projectDir = file("web/api")
project(":json-schema").projectDir = file("tools/json-schema")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            name = "EldoNexus"
            url = uri("https://eldonexus.de/repository/maven-public/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}
