plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    java
    `maven-publish`
    `java-library`
}

group = "de.eldoria"
version = "1.0"
description = "Save order based trading."
var mainPackage = "companies"
val shadebase = group as String? + "." + mainPackage + ".libs."

repositories {
    mavenCentral()
    maven("https://eldonexus.de/repository/maven-public")
    maven("https://eldonexus.de/repository/maven-proxies")
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    implementation("de.eldoria", "eldo-util", "1.10.2b-SNAPSHOT")
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

    // database
    compileOnly("com.zaxxer", "HikariCP", "5.0.0")
    compileOnly("org.mariadb.jdbc", "mariadb-java-client", "2.7.2")
    compileOnly("org.xerial", "sqlite-jdbc", "3.36.0.3")
    compileOnly("org.postgresql", "postgresql", "42.2.23")

    compileOnly("org.spigotmc", "spigot-api", "1.13.2-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains", "annotations", "20.1.0")
    compileOnly("com.github.MilkBowl", "VaultAPI", "1.7")

    testImplementation("org.jetbrains", "annotations", "19.0.0")
    testImplementation("org.spigotmc", "spigot-api", "1.16.5-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.7.1")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.7.1")
    testImplementation("org.mockito", "mockito-core", "3.5.13")
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_11
}

publishing {
    val publishData = PublishData(project)
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        groupId = project.group as String?
        artifactId = project.name.toLowerCase()
        version = publishData.getVersion()
    }

    repositories {
        maven {
            authentication {
                credentials(PasswordCredentials::class) {
                    username = System.getenv("NEXUS_USERNAME")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }

            name = "EldoNexus"
            url = uri(publishData.getRepository())
        }
    }
}

tasks {
    val data = PublishData(project)
    processResources {
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("plugin.yml") {
                expand(
                    "name" to project.name,
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

class PublishData(private val project: Project) {
    private var type: Type = getReleaseType()
    private var hashLength: Int = 7

    private fun getReleaseType(): Type {
        val branch = getCheckedOutBranch()
        return when {
            branch.contentEquals("master") -> Type.RELEASE
            branch.startsWith("dev") -> Type.DEV
            else -> Type.SNAPSHOT
        }
    }

    private fun getCheckedOutGitCommitHash(): String = System.getenv("GITHUB_SHA")?.substring(0, hashLength) ?: "local"

    private fun getCheckedOutBranch(): String = System.getenv("GITHUB_REF")?.replace("refs/heads/", "") ?: "local"

    fun getVersion(): String = getVersion(false)

    fun getVersion(appendCommit: Boolean): String =
        type.append(getVersionString(), appendCommit, getCheckedOutGitCommitHash())

    private fun getVersionString(): String = (project.version as String).replace("-SNAPSHOT", "").replace("-DEV", "")

    fun getRepository(): String = type.repo

    enum class Type(private val append: String, val repo: String, private val addCommit: Boolean) {
        RELEASE("", "https://eldonexus.de/repository/maven-releases/", false),
        DEV("-DEV", "https://eldonexus.de/repository/maven-dev/", true),
        SNAPSHOT("-SNAPSHOT", "https://eldonexus.de/repository/maven-snapshots/", true);

        fun append(name: String, appendCommit: Boolean, commitHash: String): String =
            name.plus(append).plus(if (appendCommit && addCommit) "-".plus(commitHash) else "")
    }
}
