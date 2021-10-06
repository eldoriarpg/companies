plugins {
    java
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

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
