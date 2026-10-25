plugins {
    id("companies.java-conventions")
}

dependencies {
    api(project(":companies-api"))
    implementation(project(":web-api"))
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
    compileOnly(libs.log4j.core)
    compileOnly(libs.strokkcommands.annotations)
    annotationProcessor(libs.strokkcommands.processor)
    annotationProcessor(libs.ocular)

    testImplementation(libs.sqlite)
    testImplementation(libs.vault)
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.bundles.jackson)
    testImplementation(libs.jedis)
    testImplementation(libs.log4j.core)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.postgres)
    testImplementation(libs.mariadb)
}
