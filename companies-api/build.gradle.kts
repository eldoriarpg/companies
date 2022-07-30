plugins {
    java
    id("de.chojo.publishdata") version "1.0.8"
    id("de.eldoria.library-conventions")
}

group = "de.eldoria"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.kyori:adventure-api:4.9.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

publishData{
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

tasks{
    test{
        useJUnitPlatform()
    }
}
