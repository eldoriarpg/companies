plugins {
    id("companies.java-base")
}

val libs = versionCatalogs.named("libs")

dependencies {
    compileOnly(libs.findLibrary("paper").get())
    compileOnly(libs.findLibrary("jetbrains-annotations").get())

    testImplementation(libs.findLibrary("paper").get())
    testImplementation(libs.findLibrary("mockbukkit").get())
}
