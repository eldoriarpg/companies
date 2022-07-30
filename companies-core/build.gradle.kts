plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("de.eldoria.java-conventions")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
    id("de.chojo.publishdata") version "1.0.8"
}

group = "de.eldoria"
val shadebase = project.group as String + ".companies.libs."

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":companies-api"))
    implementation("de.eldoria", "eldo-util", "1.10.3")
    implementation("de.chojo", "sql-util", "1.4.6") {
        exclude("org.jetbrains")
        exclude("org.slf4j")
        exclude("com.zaxxer")
    }

    // text
    implementation("net.kyori", "adventure-api", "4.9.1")
    implementation("net.kyori", "adventure-platform-bukkit", "4.0.0")
    implementation("net.kyori", "adventure-text-minimessage", "4.1.0-SNAPSHOT")

    compileOnly("com.comphenix.protocol", "ProtocolLib", "4.7.0")
    compileOnly("me.clip", "placeholderapi", "2.11.2")

    // database
    bukkitLibrary("com.zaxxer", "HikariCP", "5.0.0")
    bukkitLibrary("org.mariadb.jdbc", "mariadb-java-client", "2.7.2")
    bukkitLibrary("org.xerial", "sqlite-jdbc", "3.36.0.3")
    bukkitLibrary("org.postgresql", "postgresql", "42.2.23")

    compileOnly("com.github.MilkBowl", "VaultAPI", "1.7")
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

        register("companies.company.*"){
            description = "Gives access to company commands"
            children = listOf("companies.company.create")
        }

        register("companies.order.create"){
            description = "Allows creation of orders"
        }
        register("companies.admin.level"){
            description = "Allows managing of company level settings"
        }
        register("companies.company.create"){
            description = "Allows creation of a company"
        }
    }
}
