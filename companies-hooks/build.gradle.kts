plugins {
    id("companies.java-conventions")
}

repositories {
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.fancyinnovations.com/releases")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly(project(":companies-core"))
    compileOnly(libs.citizens) {
        isTransitive = false
    }
    compileOnly(libs.fancynpcs) {
        isTransitive = false
    }
    compileOnly(libs.worldguard)
    compileOnly(libs.worldedit)
    compileOnly(libs.plotsquared)

    testImplementation(project(":companies-core"))
}
