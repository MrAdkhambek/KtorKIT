// Standalone build on purpose: it consumes KtorKIT the way a real user does, from a
// repository, rather than as a project dependency. Run `publishToMavenLocal` in the root
// build first. Deliberately NOT included in the root settings.gradle.kts.
rootProject.name = "jvm-consumer"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
