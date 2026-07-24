package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KtorKitFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::KtorKitDeclarationGenerationExtension
        +::KtorKitFirCheckersExtension
    }
}
