plugins {
    id("companies.java-base")
}

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jspecify)
    compileOnly(libs.spotbugs.annotations)
}
