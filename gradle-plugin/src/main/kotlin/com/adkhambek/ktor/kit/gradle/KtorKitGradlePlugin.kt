package com.adkhambek.ktor.kit.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KtorKitGradlePlugin : KotlinCompilerPluginSupportPlugin {

    /**
     * Adds the runtime to the project's main source set once the Kotlin plugin is present.
     *
     * It has to go on the source set rather than on a project-level `implementation`
     * configuration (which only exists in JVM-shaped projects) or on each compilation
     * (which leaves the `commonMain` metadata compilation without it, so common code
     * cannot see the annotations). `commonMain` for multiplatform, `main` otherwise;
     * every target then inherits it and resolves its own variant.
     */
    override fun apply(target: Project) {
        target.plugins.withType(KotlinBasePlugin::class.java).configureEach {
            val kotlin = target.extensions.getByType(KotlinProjectExtension::class.java)
            val mainSourceSet: KotlinSourceSet =
                kotlin.sourceSets.findByName("commonMain") ?: kotlin.sourceSets.getByName("main")
            mainSourceSet.dependencies {
                implementation("$KTORKIT_GROUP:runtime:$KTORKIT_VERSION")
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "com.adkhambek.ktor.kit"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = KTORKIT_GROUP,
            artifactId = "compiler",
            version = KTORKIT_VERSION,
        )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }
}
