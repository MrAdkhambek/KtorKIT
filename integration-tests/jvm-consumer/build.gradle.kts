plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.adkhambek.ktor.kit") version "0.1.0"
}

kotlin {
    jvmToolchain(21)
}

// Note: no explicit KtorKIT runtime dependency. The Gradle plugin is responsible for
// adding it to the main source set — that is precisely what this build verifies.
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.5.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.test {
    useJUnitPlatform()
}
