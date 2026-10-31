plugins {
    id("companies.java-conventions")
}

repositories {
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.fancyinnovations.com/releases")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.GriefPrevention") }
    }
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
    compileOnly(libs.griefprevention) {
        isTransitive = false
    }

    testImplementation(project(":companies-core"))
}
