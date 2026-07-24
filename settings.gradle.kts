rootProject.name = "ktorkit"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":runtime", ":compiler", ":gradle-plugin", ":sample", ":tests-mp")
