plugins {
    id("companies.java-conventions")
}

repositories {
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.fancyinnovations.com/releases")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.william278.net/releases") {
        content { includeGroup("net.william278.huskclaims") }
    }
    maven("https://repo.incredibleplugins.com/releases") {
        content { includeGroup("com.incredibleplugins") }
    }
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
    compileOnly(libs.huskclaims) {
        isTransitive = false
    }
    compileOnly(libs.lands) {
        isTransitive = false
    }

    testImplementation(project(":companies-core"))
}
