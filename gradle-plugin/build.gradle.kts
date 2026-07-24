plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    id("publishing-convention")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(gradleApi())
}

// Bakes the published coordinates into a generated constant so the plugin never hardcodes
// the group/version of the runtime and compiler artifacts it resolves at apply time.
val generateCoordinates = tasks.register("generateCoordinates") {
    val outputDir = layout.buildDirectory.dir("generated/version")
    val groupValue = project.group.toString()
    val versionValue = project.version.toString()
    inputs.property("group", groupValue)
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("com/adkhambek/ktor/kit/gradle")
        packageDir.mkdirs()
        packageDir.resolve("KtorKitCoordinates.kt").writeText(
            """
            package com.adkhambek.ktor.kit.gradle

            internal const val KTORKIT_GROUP: String = "$groupValue"
            internal const val KTORKIT_VERSION: String = "$versionValue"
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateCoordinates)
}

gradlePlugin {
    plugins {
        create("ktorkit") {
            // Must live under the com.adkhambek namespace: Gradle derives the plugin
            // marker's Maven coordinates from this id, and Central only accepts
            // namespaces we have verified.
            id = "com.adkhambek.ktor.kit"
            implementationClass = "com.adkhambek.ktor.kit.gradle.KtorKitGradlePlugin"
            displayName = "KtorKit Compiler Plugin"
            description = "Wires the KtorKit K2 compiler plugin into kotlinc."
        }
    }
}
