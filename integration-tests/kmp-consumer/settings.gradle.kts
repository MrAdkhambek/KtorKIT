// Standalone build on purpose: it consumes KtorKIT the way a real user does, from a
// repository, rather than as a project dependency. Run `publishToMavenLocal` in the root
// build first. Deliberately NOT included in the root settings.gradle.kts.
rootProject.name = "kmp-consumer"

pluginManagement {
    // Track whatever VERSION_NAME the root build is on, so this always exercises the
    // version just published locally. Hardcoding it meant that after a version bump the
    // plugin resolved the previous release from Maven Central and silently tested that
    // instead.
    val ktorkitVersion: String = java.util.Properties().run {
        File(settingsDir, "../../gradle.properties").inputStream().use { load(it) }
        getProperty("VERSION_NAME")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.adkhambek.ktor.kit") useVersion(ktorkitVersion)
        }
    }
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
