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
    maven("https://repo.glaremasters.me/repository/towny/") {
        content { includeGroup("com.palmergames.bukkit.towny") }
    }
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.GriefPrevention")
            includeGroup("com.github.Gypopo")
            includeGroup("com.github.brcdev-minecraft")
        }
    }
    maven("https://repo.minebench.de/") {
        content { includeGroup("com.acrobot.chestshop") }
    }
    maven("https://raw.githubusercontent.com/Shopkeepers/Repository/main/releases/") {
        content { includeGroup("com.nisovin.shopkeepers") }
    }
    maven("https://repo.groupez.dev/releases") {
        content {
            includeGroup("fr.maxlego08.shop")
            includeGroup("fr.maxlego08.menu")
            includeGroup("fr.maxlego08.zauctionhouse")
        }
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
    compileOnly(libs.towny) {
        isTransitive = false
    }
    compileOnly(libs.economyshopgui) {
        isTransitive = false
    }
    compileOnly(libs.shopguiplus) {
        isTransitive = false
    }
    compileOnly(libs.zshop) {
        isTransitive = false
    }
    compileOnly(libs.zmenu) {
        isTransitive = false
    }
    compileOnly(libs.chestshop) {
        isTransitive = false
    }
    compileOnly(libs.shopkeepers) {
        isTransitive = false
    }
    compileOnly(libs.zauctionhouse) {
        isTransitive = false
    }

    testImplementation(project(":companies-core"))
    testImplementation(libs.economyshopgui) {
        isTransitive = false
    }
    testImplementation(libs.shopguiplus) {
        isTransitive = false
    }
    testImplementation(libs.zshop) {
        isTransitive = false
    }
    testImplementation(libs.zmenu) {
        isTransitive = false
    }
    testImplementation(libs.chestshop) {
        isTransitive = false
    }
    testImplementation(libs.shopkeepers) {
        isTransitive = false
    }
    testImplementation(libs.zauctionhouse) {
        isTransitive = false
    }
}
