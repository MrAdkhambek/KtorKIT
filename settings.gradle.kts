rootProject.name = "ktorkit"

pluginManagement {
    includeBuild("build-logic")
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
