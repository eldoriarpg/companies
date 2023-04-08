plugins{
    `java-library`
    `maven-publish`
}

dependencies {
    api("net.kyori", "adventure-platform-bukkit", "4.2.0")
    api("net.kyori", "adventure-text-minimessage", "4.12.0")
    api("de.eldoria.util", "jackson-configuration", "2.0.0-DEV")
    api("de.eldoria.util", "plugin", "2.0.0-DEV")
    api("de.eldoria.util", "localization", "2.0.0-DEV")
    api("de.eldoria.util", "messaging", "2.0.0-DEV")
    api("de.eldoria.util", "threading", "2.0.0-DEV")
    api("de.eldoria", "messageblocker", "1.1.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

publishData{
    useEldoNexusRepos()
    publishComponent("java")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            publishData.configurePublication(this)
            pom {
                url.set("https://github.com/eldoriarpg/companies")
                developers {
                    developer {
                        name.set("Florian Fülling")
                        organization.set("EldoriaRPG")
                        organizationUrl.set("https://github.com/eldoriarpg")
                    }
                }
                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://github.com/eldoriarpg/companies/blob/master/LICENSE.md")
                    }
                }
            }
        }

    }

    repositories {
        maven {
            name = "EldoNexus"
            url = uri(publishData.getRepository())

            authentication {
                credentials(PasswordCredentials::class) {
                    username = System.getenv("NEXUS_USERNAME")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }
        }
    }
}
