plugins {
    id("companies.publishing-conventions")
}

publishData {
    eldoNexus(dev = false)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all", "-quiet")
        addBooleanOption("Werror", true)
    }
}
