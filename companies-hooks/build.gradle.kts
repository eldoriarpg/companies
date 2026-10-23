plugins {
    id("companies.java-conventions")
}

repositories {
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.fancyinnovations.com/releases")
}

dependencies {
    compileOnly(project(":companies-core"))
    compileOnly(libs.citizens) {
        isTransitive = false
    }
    compileOnly(libs.fancynpcs) {
        isTransitive = false
    }

    testImplementation(project(":companies-core"))
}
