plugins {
    id("companies.java-conventions")
}

dependencies {
    api(project(":companies-api"))
    implementation(libs.bundles.sadu)

    api(libs.bundles.eldoria.utilities) {
        exclude("net.kyori")
    }
    api(libs.messageblocker)

    compileOnly(libs.papi)
    compileOnly(libs.vault)
    compileOnly(platform(libs.jackson.bom))
    compileOnly(libs.bundles.jackson)
    compileOnly(libs.bundles.database)

    testImplementation(libs.sqlite)
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.bundles.jackson)
}
