import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm {
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    js(IR) {
        nodejs {
            testTask {
                useMocha()
            }
        }
    }
    // Native target so plugin-generated code is exercised on Kotlin/Native too.
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":runtime"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

val compilerJar = project(":compiler").tasks.named<Jar>("jar")

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(compilerJar)
    compilerOptions.freeCompilerArgs.add(
        compilerJar.flatMap { it.archiveFile }.map { "-Xplugin=${it.asFile.absolutePath}" }
    )
}
