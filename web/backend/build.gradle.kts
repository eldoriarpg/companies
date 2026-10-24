plugins {
    id("companies.java-base")
    application
}

dependencies {
    implementation(project(":web-api"))
    implementation(libs.javalin)
    implementation(libs.bundles.sadu.postgresql)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.jspecify)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.testcontainers.postgresql)
    testCompileOnly(libs.spotbugs.annotations)
}

application {
    mainClass = "de.eldoria.companies.web.gateway.GatewayService"
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("companies.messages", project(":web-api").file("src/test/resources/messages").absolutePath)
}
