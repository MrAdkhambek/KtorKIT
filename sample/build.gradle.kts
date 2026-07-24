import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":runtime"))
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kctfork.core)
    testImplementation(project(":compiler"))
    testImplementation(libs.kotlin.compiler.embeddable)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

application {
    mainClass = "sample.MainKt"
}

val compilerJar = project(":compiler").tasks.named<Jar>("jar")

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(compilerJar)
    compilerOptions.freeCompilerArgs.add(
        compilerJar.flatMap { it.archiveFile }.map { "-Xplugin=${it.asFile.absolutePath}" }
    )
    compilerOptions.freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
}
