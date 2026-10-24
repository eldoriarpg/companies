plugins {
    id("companies.java-base")
}

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jspecify)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(project(":json-schema"))
    testImplementation(libs.json.schema.validator)
}

tasks.register<JavaExec>("generateSchema") {
    group = "build"
    description = "Writes the JSON Schema of the current protocol version to src/main/resources/schema"
    dependsOn("compileTestJava")
    mainClass = "de.eldoria.companies.web.api.ProtocolSchemaCli"
    classpath = sourceSets.test.get().runtimeClasspath
    workingDir = projectDir
}
