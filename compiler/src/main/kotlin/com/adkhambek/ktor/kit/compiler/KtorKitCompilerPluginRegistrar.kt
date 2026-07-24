package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class KtorKitCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.adkhambek.ktor.kit"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(
            CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            MessageCollector.NONE,
        )
        FirExtensionRegistrarAdapter.registerExtension(KtorKitFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(KtorKitIrGenerationExtension(messageCollector))
    }
}
