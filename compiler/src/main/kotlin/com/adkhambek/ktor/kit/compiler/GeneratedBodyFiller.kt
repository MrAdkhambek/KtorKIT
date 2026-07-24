package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Value parameters, excluding dispatch/extension receivers. */
internal val IrFunction.regularParameters: List<IrValueParameter>
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

/** Declarations this plugin synthesized, identified by origin rather than by name. */
private val IrDeclaration.isKtorKitGenerated: Boolean
    get() = (origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == KtorKitPluginKey

/**
 * The function itself plus everything it overrides. Annotations live on the interface
 * declaration, never on the generated override, so every annotation lookup searches here.
 */
private val IrSimpleFunction.selfAndOverridden: List<IrSimpleFunction>
    get() = listOf(this) + allOverridden()

private fun IrSimpleFunction.findKtorKitAnnotation(fqName: FqName): IrConstructorCall? =
    selfAndOverridden.firstNotNullOfOrNull { it.getAnnotation(fqName) }

class GeneratedBodyFiller(
    private val pluginContext: IrPluginContext,
    // Deliberately required: defaulting to MessageCollector.NONE would silently turn every
    // reported error into a no-op, downgrading a compile error to a runtime crash.
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoid() {

    /**
     * Reports a user-facing error and yields a `Nothing`-typed placeholder. Because
     * `kotlin.error` returns `Nothing` the stub type-checks wherever a value was
     * expected, so the build fails on the reported diagnostic rather than on an
     * internal exception with a stack trace.
     */
    private fun IrBuilderWithScope.failWith(message: String): IrExpression {
        messageCollector.report(CompilerMessageSeverity.ERROR, message)
        return irCall(errorFn).apply { arguments[0] = irString(message) }
    }

    private val ktorClientClassSymbol by lazy {
        pluginContext.referenceClass(KtorKitNames.KTOR_CLIENT_ID)
            ?: error("Cannot find com.adkhambek.ktor.kit.KtorClient")
    }

    private fun rbFn(name: String) =
        pluginContext.referenceFunctions(
            CallableId(KtorKitNames.REQUEST_BUILDER_ID, Name.identifier(name))
        ).first()

    private val beginRequestFn by lazy {
        pluginContext.referenceFunctions(
            CallableId(KtorKitNames.PKG, Name.identifier("beginRequest"))
        ).first()
    }
    private val rbPathFn by lazy { rbFn("path") }
    private val rbQueryFn by lazy { rbFn("query") }
    private val rbBodyFn by lazy { rbFn("body") }
    private val rbHeaderFn by lazy { rbFn("header") }
    private val rbHeaderMapFn by lazy { rbFn("headerMap") }
    private val rbQueryMapFn by lazy { rbFn("queryMap") }
    private val rbFormEncodedFn by lazy { rbFn("formEncoded") }
    private val rbFieldFn by lazy { rbFn("field") }
    private val rbFieldMapFn by lazy { rbFn("fieldMap") }
    private val rbBodyJsonFn by lazy { rbFn("bodyJson") }
    private val rbMultipartFn by lazy { rbFn("multipart") }
    private val rbPartFn by lazy { rbFn("part") }
    private val rbExecuteAsFlowFn by lazy { rbFn("executeAsFlow") }
    private val rbExecuteAsFlowOfStringFn by lazy { rbFn("executeAsFlowOfString") }
    private val rbUseBaseUrlFn by lazy { rbFn("useBaseUrl") }
    private val rbUseUrlFn by lazy { rbFn("useUrl") }
    private val rbExecuteFn by lazy { rbFn("executeAsString") }
    private val rbExecuteWithDeserializerFn by lazy { rbFn("executeWithDeserializer") }
    private val rbExecuteAsResponseStringFn by lazy { rbFn("executeAsResponseString") }
    private val rbExecuteAsResponseWithDeserializerFn by lazy { rbFn("executeAsResponseWithDeserializer") }

    private val builtinsPkg = FqName("kotlinx.serialization.builtins")
    private val mapSerializerFn by lazy {
        pluginContext.referenceFunctions(CallableId(builtinsPkg, Name.identifier("MapSerializer")))
            .firstOrNull { it.owner.regularParameters.size == 2 }
            ?: error("kotlinx.serialization.builtins.MapSerializer not found")
    }
    private val listSerializerFn by lazy {
        pluginContext.referenceFunctions(CallableId(builtinsPkg, Name.identifier("ListSerializer")))
            .firstOrNull { it.owner.regularParameters.size == 1 }
            ?: error("kotlinx.serialization.builtins.ListSerializer not found")
    }
    /**
     * Builtin `serializer()` extensions indexed by the FQN of their Companion receiver.
     * Built once: the linear form rebuilt ~16 fully-qualified name strings on every
     * lookup, and every user `@Serializable` type paid that full scan before missing.
     */
    private val primitiveSerializerByReceiver: Map<String, IrSimpleFunctionSymbol> by lazy {
        pluginContext.referenceFunctions(CallableId(builtinsPkg, Name.identifier("serializer")))
            .mapNotNull { fn ->
                val receiverFqn = fn.owner.parameters
                    .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
                    ?.type?.classFqName?.asString()
                receiverFqn?.let { it to fn }
            }
            .toMap()
    }
    private val nullableGetter by lazy {
        val prop = pluginContext.referenceProperties(CallableId(builtinsPkg, Name.identifier("nullable")))
            .firstOrNull() ?: error("kotlinx.serialization.builtins.nullable not found")
        prop.owner.getter?.symbol ?: error("nullable property has no getter")
    }
    private val errorFn by lazy {
        pluginContext.referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("error"))).first()
    }

    override fun visitClass(declaration: IrClass): IrStatement {
        if (declaration.isKtorKitGenerated) {
            processImplClass(declaration)
        }
        return super.visitClass(declaration)
    }

    private fun processImplClass(implClass: IrClass) {
        val ktorClientType = ktorClientClassSymbol.defaultType
        val clientField = implClass.addField {
            name = Name.identifier("client")
            type = ktorClientType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
        }

        val ctor = implClass.primaryConstructor ?: return
        if (ctor.isKtorKitGenerated && ctor.body == null) {
            fillConstructorBody(ctor, implClass, clientField)
        }

        for (fn in implClass.functions) {
            if (!fn.isKtorKitGenerated || fn.body != null) continue
            fillMethodBody(fn, clientField)
        }
    }

    private fun fillConstructorBody(ctor: IrConstructor, implClass: IrClass, clientField: IrField) {
        val clientParam = ctor.regularParameters.first()
        val anyCtor = pluginContext.irBuiltIns.anyClass.owner.primaryConstructor
            ?: error("kotlin.Any has no primary constructor")
        val builder = DeclarationIrBuilder(pluginContext, ctor.symbol)
        ctor.body = builder.irBlockBody {
            +irDelegatingConstructorCall(anyCtor)
            +IrInstanceInitializerCallImpl(
                startOffset, endOffset,
                implClass.symbol,
                pluginContext.irBuiltIns.unitType,
            )
            +irSetField(
                receiver = irGet(implClass.thisReceiver!!),
                field = clientField,
                value = irGet(clientParam),
            )
        }
    }

    private fun fillMethodBody(fn: IrSimpleFunction, clientField: IrField) {
        val httpInfo = httpMethodOf(fn)
        if (httpInfo == null) {
            stubMethodBody(fn)
            return
        }
        val (verb, pathTemplate) = httpInfo

        val builder = DeclarationIrBuilder(pluginContext, fn.symbol)
        val thisParam = fn.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
            ?: error("expected dispatch receiver on ${fn.name}")
        fn.body = builder.irBlockBody {
            // beginRequest is an extension function: arguments[0] is its receiver.
            val rb = irTemporary(
                irCall(beginRequestFn).apply {
                    arguments[0] = irGetField(irGet(thisParam), clientField)
                    arguments[1] = irString(verb)
                    arguments[2] = irString(pathTemplate)
                },
                nameHint = "rb",
            )
            interfaceBaseUrlOf(fn)?.let { baseUrl ->
                +irCall(rbUseBaseUrlFn).apply {
                    arguments[0] = irGet(rb)
                    arguments[1] = irString(baseUrl)
                }
            }
            for ((hName, hValue) in staticHeadersOf(fn)) {
                +irCall(rbHeaderFn).apply {
                    arguments[0] = irGet(rb)
                    arguments[1] = irString(hName)
                    arguments[2] = irString(hValue)
                }
            }
            if (isFormEncoded(fn)) {
                +irCall(rbFormEncodedFn).apply { arguments[0] = irGet(rb) }
            }
            if (isMultipart(fn)) {
                +irCall(rbMultipartFn).apply { arguments[0] = irGet(rb) }
            }
            val overriddenParams = fn.allOverridden().map { it.regularParameters }
            for ((index, param) in fn.regularParameters.withIndex()) {
                bindParameter(rb, param, index, overriddenParams)
            }
            +irReturn(buildExecuteCall(rb, fn))
        }
    }

    /**
     * Emits the single `RequestBuilder` call this parameter maps to.
     *
     * Bindings are resolved in declaration order and stop at the first match, so a
     * parameter costs one annotation lookup rather than one per supported annotation.
     */
    private fun IrBlockBodyBuilder.bindParameter(
        rb: IrVariable,
        param: IrValueParameter,
        index: Int,
        overriddenParams: List<List<IrValueParameter>>,
    ) {
        val (fqName, annotation) = bindingOrder
            .firstNotNullOfOrNull { fq ->
                paramAnnotation(param, index, overriddenParams, fq)?.let { fq to it }
            }
            ?: return

        // `(name, value)` shape — the annotation's string argument names the binding.
        fun named(symbol: IrSimpleFunctionSymbol) = irCall(symbol).apply {
            arguments[0] = irGet(rb)
            arguments[1] = irString(constStringArg(annotation))
            arguments[2] = irGet(param)
        }
        // `(value)` shape — the whole parameter is passed through.
        fun value(symbol: IrSimpleFunctionSymbol) = irCall(symbol).apply {
            arguments[0] = irGet(rb)
            arguments[1] = irGet(param)
        }

        when (fqName) {
            KtorKitNames.PATH -> +named(rbPathFn)
            KtorKitNames.QUERY -> +named(rbQueryFn)
            KtorKitNames.HEADER -> +named(rbHeaderFn)
            KtorKitNames.FIELD -> +named(rbFieldFn)
            KtorKitNames.HEADER_MAP -> +value(rbHeaderMapFn)
            KtorKitNames.QUERY_MAP -> +value(rbQueryMapFn)
            KtorKitNames.FIELD_MAP -> +value(rbFieldMapFn)
            KtorKitNames.URL -> +value(rbUseUrlFn)
            KtorKitNames.PART -> +irCall(rbPartFn).apply {
                arguments[0] = irGet(rb)
                arguments[1] = irString(constStringArg(annotation))
                arguments[2] = irString(constStringArgAt(annotation, 1) ?: "")
                arguments[3] = irGet(param)
            }
            KtorKitNames.BODY -> {
                val bodyType = param.type
                if (bodyType.classFqName == KtorKitNames.STRING_FQ_NAME) {
                    +value(rbBodyFn)
                } else {
                    // Symmetric with return types: serialize @Body via kotlinx.serialization
                    val bodySerializer = resolveSerializer(bodyType)
                    +irCall(rbBodyJsonFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irGet(param)
                        arguments[2] = bodySerializer
                        typeArguments[0] = bodyType
                    }
                }
            }
        }
    }

    /**
     * Binding annotations in precedence order — the first one present on a parameter wins,
     * which is the order the previous chained `when` encoded.
     */
    private val bindingOrder = listOf(
        KtorKitNames.PATH,
        KtorKitNames.QUERY,
        KtorKitNames.HEADER,
        KtorKitNames.HEADER_MAP,
        KtorKitNames.QUERY_MAP,
        KtorKitNames.BODY,
        KtorKitNames.FIELD,
        KtorKitNames.FIELD_MAP,
        KtorKitNames.PART,
        KtorKitNames.URL,
    )

    private fun IrBlockBodyBuilder.buildExecuteCall(
        rb: IrVariable,
        fn: IrSimpleFunction,
    ): IrExpression {
        val returnType = fn.returnType
        val returnFqn = returnType.classFqName

        wrappedExecute(rb, returnType, returnFqn, KtorKitNames.FLOW_FQ_NAME, rbExecuteAsFlowOfStringFn, rbExecuteAsFlowFn)
            ?.let { return it }
        wrappedExecute(rb, returnType, returnFqn, KtorKitNames.RESPONSE_FQ_NAME, rbExecuteAsResponseStringFn, rbExecuteAsResponseWithDeserializerFn)
            ?.let { return it }

        if (returnFqn == KtorKitNames.STRING_FQ_NAME) {
            return irCall(rbExecuteFn).apply { arguments[0] = irGet(rb) }
        }

        val serializerCall = resolveSerializer(returnType)
        return irCall(rbExecuteWithDeserializerFn).apply {
            arguments[0] = irGet(rb)
            arguments[1] = serializerCall
            typeArguments[0] = returnType
        }
    }

    /**
     * Handles the `Wrapper<T>` return shapes (`Flow<T>`, `Response<T>`), which differ only
     * in which runtime entry points they call. Returns null when [returnType] is not the
     * given wrapper.
     */
    private fun IrBlockBodyBuilder.wrappedExecute(
        rb: IrVariable,
        returnType: IrType,
        returnFqn: FqName?,
        wrapperFqn: FqName,
        stringFn: IrSimpleFunctionSymbol,
        typedFn: IrSimpleFunctionSymbol,
    ): IrExpression? {
        if (returnFqn != wrapperFqn || returnType !is IrSimpleType) return null
        val inner = returnType.arguments.firstOrNull()?.typeOrNull
            ?: return failWith(
                "ktorkit: ${wrapperFqn.shortName()}<*> needs a concrete type argument; " +
                    "star projections are not supported."
            )
        return if (inner.classFqName == KtorKitNames.STRING_FQ_NAME) {
            irCall(stringFn).apply { arguments[0] = irGet(rb) }
        } else {
            val ser = resolveSerializer(inner)
            irCall(typedFn).apply {
                arguments[0] = irGet(rb)
                arguments[1] = ser
                typeArguments[0] = inner
            }
        }
    }

    private fun IrBuilderWithScope.resolveSerializer(type: IrType): IrExpression {
        if (type.isMarkedNullable()) {
            val baseType = type.makeNotNull()
            // `nullable` is an extension property: arguments[0] is its receiver.
            return irCall(nullableGetter).apply {
                arguments[0] = resolveSerializer(baseType)
                typeArguments[0] = baseType
            }
        }
        val fqn = type.classFqName
        if (fqn == KtorKitNames.LIST_FQ_NAME && type is IrSimpleType) {
            val elemType = type.arguments.firstOrNull()?.typeOrNull
                ?: return failWith(starProjectionMessage("List"))
            return irCall(listSerializerFn).apply {
                arguments[0] = resolveSerializer(elemType)
                typeArguments[0] = elemType
            }
        }
        if (fqn == KtorKitNames.MAP_FQ_NAME && type is IrSimpleType) {
            val keyType = type.arguments.getOrNull(0)?.typeOrNull
                ?: return failWith(starProjectionMessage("Map"))
            val valueType = type.arguments.getOrNull(1)?.typeOrNull
                ?: return failWith(starProjectionMessage("Map"))
            return irCall(mapSerializerFn).apply {
                arguments[0] = resolveSerializer(keyType)
                arguments[1] = resolveSerializer(valueType)
                typeArguments[0] = keyType
                typeArguments[1] = valueType
            }
        }
        // Built-in primitives (String, Int, Long, Boolean, ...) — extension fn on Companion
        primitiveSerializerCall(type, fqn?.asString())?.let { return it }
        val notSerializable =
            "ktorkit: cannot build a serializer for '${fqn?.asString() ?: type.render()}'. " +
                "Annotate the type with @Serializable and apply the " +
                "kotlinx-serialization compiler plugin."
        val companion = type.classOrNull?.owner?.companionObject()
            ?: return failWith(notSerializable)
        val serializerFn = companion.functions.firstOrNull {
            it.name.asString() == "serializer" && it.regularParameters.isEmpty()
        } ?: return failWith(notSerializable)
        return irCall(serializerFn.symbol).apply {
            arguments[0] = irGetObject(companion.symbol)
        }
    }

    private fun starProjectionMessage(wrapper: String) =
        "ktorkit: $wrapper<*> needs a concrete type argument; star projections are not supported."

    private fun stubMethodBody(fn: IrSimpleFunction) {
        val builder = DeclarationIrBuilder(pluginContext, fn.symbol)
        fn.body = builder.irBlockBody {
            +irReturn(
                irCall(errorFn).apply {
                    arguments[0] = irString("ktorkit: ${fn.name} has no HTTP annotation")
                }
            )
        }
    }

    private fun IrBuilderWithScope.primitiveSerializerCall(type: IrType, fqn: String?): IrExpression? {
        if (fqn == null) return null
        val companion = type.classOrNull?.owner?.companionObject() ?: return null
        val match = primitiveSerializerByReceiver["$fqn.Companion"] ?: return null
        return irCall(match).apply { arguments[0] = irGetObject(companion.symbol) }
    }

    private fun interfaceBaseUrlOf(fn: IrSimpleFunction): String? {
        // fn lives on Impl; Impl is nested in the interface
        val implClass = fn.parent as? IrClass ?: return null
        val ifaceClass = implClass.parent as? IrClass ?: return null
        val ann = ifaceClass.getAnnotation(KtorKitNames.CONTRIBUTES_API) ?: return null
        val raw = (ann.arguments.getOrNull(0) as? IrConst)?.value as? String ?: return null
        return raw.takeIf { it.isNotEmpty() }
    }

    private fun isFormEncoded(fn: IrSimpleFunction): Boolean =
        fn.findKtorKitAnnotation(KtorKitNames.FORM_URL_ENCODED) != null

    private fun isMultipart(fn: IrSimpleFunction): Boolean =
        fn.findKtorKitAnnotation(KtorKitNames.MULTIPART) != null

    private fun staticHeadersOf(fn: IrSimpleFunction): List<Pair<String, String>> {
        val ann = fn.findKtorKitAnnotation(KtorKitNames.HEADERS) ?: return emptyList()
        val vararg = ann.arguments.getOrNull(0) as? IrVararg ?: return emptyList()
        return vararg.elements.mapNotNull { el ->
            val raw = (el as? IrConst)?.value as? String ?: return@mapNotNull null
            val colonIdx = raw.indexOf(':')
            if (colonIdx <= 0) return@mapNotNull null
            raw.substring(0, colonIdx).trim() to raw.substring(colonIdx + 1).trim()
        }
    }

    private fun httpMethodOf(fn: IrSimpleFunction): Pair<String, String>? =
        KtorKitNames.HTTP_VERB_FQ_NAMES.firstNotNullOfOrNull { (verb, fqName) ->
            fn.findKtorKitAnnotation(fqName)?.let { verb to constStringArg(it) }
        }

    /**
     * Annotations live on the interface declaration, not on the generated override,
     * so fall back through the overridden declarations by parameter position.
     * [overriddenParams] is computed once per function rather than per lookup.
     */
    private fun paramAnnotation(
        param: IrValueParameter,
        index: Int,
        overriddenParams: List<List<IrValueParameter>>,
        fqName: FqName,
    ): IrConstructorCall? {
        param.getAnnotation(fqName)?.let { return it }
        return overriddenParams.firstNotNullOfOrNull { params ->
            params.getOrNull(index)?.getAnnotation(fqName)
        }
    }

    private fun constStringArg(ann: IrConstructorCall): String {
        val arg = ann.arguments.getOrNull(0) as? IrConst
            ?: error("annotation argument must be a constant String")
        return arg.value as String
    }

    /** Reads an optional constant String argument; null when the default was used. */
    private fun constStringArgAt(ann: IrConstructorCall, index: Int): String? =
        (ann.arguments.getOrNull(index) as? IrConst)?.value as? String
}
