package io.ktorkit.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KtorKitIrGenerationExtension(
    private val messageCollector: MessageCollector = MessageCollector.NONE,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transform(GeneratedBodyFiller(pluginContext, messageCollector), null)
        moduleFragment.transform(CreateCallTransformer(pluginContext), null)
    }
}
