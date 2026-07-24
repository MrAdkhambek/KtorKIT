plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.adkhambek.ktor.kit")
}

kotlin {
    jvmToolchain(21)
    jvm()
    js(IR) { nodejs() }

    sourceSets {
        // Note: no explicit KtorKIT runtime dependency. The Gradle plugin is responsible
        // for adding it to commonMain — that is precisely what this build verifies.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.5.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
