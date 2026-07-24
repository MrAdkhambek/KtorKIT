plugins {
    alias(libs.plugins.kotlin.jvm)
    id("publishing-convention")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
            "-opt-in=org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI",
            // We intentionally inspect only user-written declarations (e.g. to detect a
            // hand-rolled Impl), which is exactly what direct access provides.
            "-opt-in=org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess",
            // FIR checkers declare `check` with context parameters as of Kotlin 2.2.
            "-Xcontext-parameters",
        )
    }
}
