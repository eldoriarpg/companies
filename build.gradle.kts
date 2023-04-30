import com.diffplug.gradle.spotless.SpotlessPlugin
import de.chojo.PublishData

plugins {
    java
    id("com.diffplug.spotless") version "6.16.0"
    id("de.chojo.publishdata") version "1.2.4"
    `maven-publish`
}

group = "de.eldoria"
version = "1.0.4"

subprojects {
    apply {
        plugin<SpotlessPlugin>()
        plugin<JavaPlugin>()
        plugin<PublishData>()
        plugin<MavenPublishPlugin>()
    }
}

allprojects {
    repositories {
//        mavenLocal {
//            content {
//                includeGroup("de.eldoria.util")
//            }
//        }
        mavenCentral()
        maven("https://eldonexus.de/repository/maven-proxies/")
        maven("https://eldonexus.de/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://repo.dmulloy2.net/repository/public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }


    dependencies {
        compileOnly("io.papermc.paper", "paper-api", "1.19.4-R0.1-SNAPSHOT")
        compileOnly("org.jetbrains", "annotations", "24.0.1")

        testImplementation("org.spigotmc", "spigot-api", "1.16.5-R0.1-SNAPSHOT")
        testImplementation(platform("org.junit:junit-bom:5.9.3"))
        testImplementation("com.github.seeseemelk", "MockBukkit-v1.16", "1.5.2")
        testImplementation("org.junit.jupiter", "junit-jupiter")
    }

    spotless {
        java {
            licenseHeaderFile(rootProject.file("HEADER.txt"))
            target("**/*.java")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks {
        compileJava {
            options.encoding = "UTF-8"
        }

        compileTestJava {
            options.encoding = "UTF-8"
        }

        test {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}
