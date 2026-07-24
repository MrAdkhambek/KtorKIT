package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KtorKitIrGenerationExtension(
    // Required, not defaulted: silently dropping diagnostics would turn a clean compile
    // error into a runtime crash in the generated code.
    private val messageCollector: MessageCollector,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transform(GeneratedBodyFiller(pluginContext, messageCollector), null)
        moduleFragment.transform(CreateCallTransformer(pluginContext), null)
    }
}
