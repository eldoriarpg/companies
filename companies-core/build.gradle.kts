plugins {
    java
    `java-library`
}

dependencies {
    api(project(":companies-api"))
    implementation(libs.bundles.sadu)

    api(libs.bundles.eldoria.utilities) {
        exclude("com.fasterxml.jackson.dataformat")
        exclude("com.fasterxml.jackson.core")
        exclude("com.fasterxml.jackson")
        exclude("net.kyori")
    }
    api(libs.messageblocker)

    compileOnly(libs.protocollib)
    compileOnly(libs.papi)
    compileOnly(libs.vault)

    compileOnly(libs.bundles.adventure)

    compileOnly(libs.bundles.jackson)

    // database
    compileOnly(libs.bundles.database)

    testImplementation(libs.bundles.adventure)
}
