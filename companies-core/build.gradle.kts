plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("de.eldoria.java-conventions")
}

group = "de.eldoria"
val shadebase = project.group as String + ".companies.libs."

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":companies-api"))
    implementation("de.eldoria", "eldo-util", "1.10.3")
    implementation("de.chojo", "sql-util", "1.1.4-DEV") {
        exclude("org.jetbrains")
        exclude("org.slf4j")
        exclude("com.zaxxer")
    }

    // text
    implementation("net.kyori", "adventure-api", "4.9.1")
    implementation("net.kyori", "adventure-platform-bukkit", "4.0.0")
    implementation("net.kyori", "adventure-text-minimessage", "4.1.0-SNAPSHOT")

    compileOnly("com.comphenix.protocol", "ProtocolLib", "4.7.0")
    compileOnly("me.clip", "placeholderapi", "2.10.10")

    // database
    compileOnly("com.zaxxer", "HikariCP", "5.0.0")
    compileOnly("org.mariadb.jdbc", "mariadb-java-client", "2.7.2")
    compileOnly("org.xerial", "sqlite-jdbc", "3.36.0.3")
    compileOnly("org.postgresql", "postgresql", "42.2.23")

    compileOnly("com.github.MilkBowl", "VaultAPI", "1.7")
}

tasks {
    val data = PublishData(project)
    processResources {
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("plugin.yml") {
                expand(
                    "name" to project.rootProject.name,
                    "version" to data.getVersion(true),
                    "description" to project.description
                )
            }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    shadowJar {
        //relocate("de.eldoria.eldoutilities", shadebase + "eldoutilities")
        relocate("net.kyori", shadebase + "kyori")
        //relocate("de.chojo.sqlutil", shadebase + "sqlutil")
        mergeServiceFiles()
        minimize()
        archiveClassifier.set("")
    }

    test {
        useJUnit()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    register<Copy>("copyToServer") {
        val path = project.property("targetDir") ?: "";
        if (path.toString().isEmpty()) {
            println("targetDir is not set in gradle properties")
            return@register
        }
        from(shadowJar)
        destinationDir = File(path.toString())
    }
}
