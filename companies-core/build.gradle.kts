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
    compileOnly(libs.jedis)
    compileOnly(libs.strokkcommands.annotations)
    annotationProcessor(libs.strokkcommands.processor)

    testImplementation(libs.sqlite)
    testImplementation(libs.vault)
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.bundles.jackson)
    testImplementation(libs.jedis)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.postgres)
    testImplementation(libs.mariadb)
}
