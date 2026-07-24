package com.adkhambek.ktor.kit.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KtorKitGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.dependencies.add("implementation", "$KTORKIT_GROUP:runtime:$KTORKIT_VERSION")
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
