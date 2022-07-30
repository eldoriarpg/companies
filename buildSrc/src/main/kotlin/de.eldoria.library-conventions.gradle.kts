plugins {
    `java-library`
    `maven-publish`
    id("de.eldoria.java-conventions")
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_11
}
