package io.ktorkit.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

class CreateCallTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {

    private val createFqn = FqName("io.ktorkit.create")

    override fun visitCall(expression: IrCall): IrExpression {
        val function = expression.symbol.owner
        if (function.kotlinFqName != createFqn) {
            return super.visitCall(expression)
        }

        val typeArg = expression.typeArguments.getOrNull(0)
            ?: error("create<T>() requires a reified type argument (offset=${expression.startOffset})")

        val targetClass: IrClass = typeArg.classOrNull?.owner
            ?: error("create<T>() type argument must be a class type, got: ${typeArg.render()}")

        val implClass = targetClass.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name.asString() == "Impl" }
            ?: error("@ContributesAPI ${targetClass.kotlinFqName} must declare a nested class named 'Impl'")

        val implCtor = implClass.primaryConstructor
            ?: error("${implClass.kotlinFqName} must have a primary constructor")

        val ctorValueParams = implCtor.parameters.count { it.kind == IrParameterKind.Regular }
        require(ctorValueParams == 1) {
            "${implClass.kotlinFqName}.<init> must take exactly one parameter (the KtorClient); " +
                "found $ctorValueParams"
        }

        // The extension receiver of create() is the KtorClient the Impl needs.
        val receiverIndex = function.parameters.indexOfFirst { it.kind == IrParameterKind.ExtensionReceiver }
        val receiver = expression.arguments.getOrNull(receiverIndex)
            ?: error("create() must be called on a KtorClient receiver")

        val builder = DeclarationIrBuilder(
            generatorContext = pluginContext,
            symbol = expression.symbol,
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
        )

        return builder.irCall(implCtor.symbol).apply {
            arguments[0] = receiver
        }
    }
}
