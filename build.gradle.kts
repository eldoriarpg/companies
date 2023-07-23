import com.diffplug.gradle.spotless.SpotlessPlugin
import de.chojo.PublishData

plugins {
    java
    alias(libs.plugins.spotless)
    alias(libs.plugins.publishdata)
    `maven-publish`
}

group = "de.eldoria"
version = "1.0.5"

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
        val libs = rootProject.libs
        val testlibs = rootProject.testlibs
        compileOnly(libs.paper.latest)
        compileOnly(libs.jetbrains.annotations)

        testImplementation(libs.spigot.v16)
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation(testlibs.mockbukkit)
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
