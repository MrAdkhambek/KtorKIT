plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
    jvm()
    js(IR) {
        nodejs()
    }

    // Apple
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    watchosArm64()
    watchosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()

    // Desktop / server
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
        }
    }
}
