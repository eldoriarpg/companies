plugins {
    `java-library`
    java
    `maven-publish`
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

publishData {
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
