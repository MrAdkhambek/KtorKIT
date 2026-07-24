plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
    compileOnly(gradleApi())
}

// Bakes the project version into a generated constant so the plugin never
// hardcodes the coordinates of the runtime/compiler artifacts it resolves.
val generateVersionConstant by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version")
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("io/ktorkit/gradle")
        packageDir.mkdirs()
        packageDir.resolve("KtorKitVersion.kt").writeText(
            """
            package io.ktorkit.gradle

            internal const val KTORKIT_VERSION: String = "$versionValue"
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateVersionConstant)
}

gradlePlugin {
    plugins {
        create("ktorkit") {
            id = "io.ktorkit"
            implementationClass = "io.ktorkit.gradle.KtorKitGradlePlugin"
            displayName = "KtorKit Compiler Plugin"
            description = "Wires the KtorKit K2 compiler plugin into kotlinc."
        }
    }
}
