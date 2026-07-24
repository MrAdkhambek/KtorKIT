package io.ktorkit.compiler

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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeOrNull
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
private val IrSimpleFunction.regularParameters: List<IrValueParameter>
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

private val IrConstructor.regularParameters: List<IrValueParameter>
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

class GeneratedBodyFiller(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector = MessageCollector.NONE,
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

    private val ktorkitPkg = FqName("io.ktorkit")
    private val httpVerbs = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    private val ktorClientClassSymbol by lazy {
        pluginContext.referenceClass(ClassId(ktorkitPkg, Name.identifier("KtorClient")))
            ?: error("Cannot find io.ktorkit.KtorClient")
    }
    private val requestBuilderClassId = ClassId(ktorkitPkg, Name.identifier("RequestBuilder"))

    private fun rbFn(name: String) =
        pluginContext.referenceFunctions(CallableId(requestBuilderClassId, Name.identifier(name))).first()

    private val beginRequestFn by lazy {
        pluginContext.referenceFunctions(CallableId(ktorkitPkg, Name.identifier("beginRequest"))).first()
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
    private val builtinsPrimitiveSerializers by lazy {
        pluginContext.referenceFunctions(CallableId(builtinsPkg, Name.identifier("serializer")))
            .filter { fn -> fn.owner.parameters.any { it.kind == IrParameterKind.ExtensionReceiver } }
    }
    private val nullableGetter by lazy {
        val prop = pluginContext.referenceProperties(CallableId(builtinsPkg, Name.identifier("nullable")))
            .firstOrNull() ?: error("kotlinx.serialization.builtins.nullable not found")
        prop.owner.getter?.symbol ?: error("nullable property has no getter")
    }
    private val errorFn by lazy {
        pluginContext.referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("error"))).first()
    }

    private fun isOurClass(c: IrClass): Boolean {
        val o = c.origin
        return o is IrDeclarationOrigin.GeneratedByPlugin && o.pluginKey == KtorKitPluginKey
    }

    private fun isOurFunction(f: IrSimpleFunction): Boolean {
        val o = f.origin
        return o is IrDeclarationOrigin.GeneratedByPlugin && o.pluginKey == KtorKitPluginKey
    }

    private fun isOurConstructor(c: IrConstructor): Boolean {
        val o = c.origin
        return o is IrDeclarationOrigin.GeneratedByPlugin && o.pluginKey == KtorKitPluginKey
    }

    override fun visitClass(declaration: IrClass): IrStatement {
        if (isOurClass(declaration)) {
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
        if (isOurConstructor(ctor) && ctor.body == null) {
            fillConstructorBody(ctor, implClass, clientField)
        }

        for (fn in implClass.functions) {
            if (!isOurFunction(fn) || fn.body != null) continue
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
            for (param in fn.regularParameters) {
                val pathAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Path")))
                val queryAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Query")))
                val headerAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Header")))
                val headerMapAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("HeaderMap")))
                val queryMapAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("QueryMap")))
                val bodyAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Body")))
                val fieldAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Field")))
                val fieldMapAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("FieldMap")))
                val partAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Part")))
                val urlAnn = paramAnnotation(param, fn, ktorkitPkg.child(Name.identifier("Url")))
                when {
                    pathAnn != null -> +irCall(rbPathFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irString(constStringArg(pathAnn))
                        arguments[2] = irGet(param)
                    }
                    queryAnn != null -> +irCall(rbQueryFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irString(constStringArg(queryAnn))
                        arguments[2] = irGet(param)
                    }
                    headerAnn != null -> +irCall(rbHeaderFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irString(constStringArg(headerAnn))
                        arguments[2] = irGet(param)
                    }
                    headerMapAnn != null -> +irCall(rbHeaderMapFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irGet(param)
                    }
                    queryMapAnn != null -> +irCall(rbQueryMapFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irGet(param)
                    }
                    bodyAnn != null -> {
                        val bodyType = param.type
                        if (bodyType.classFqName == FqName("kotlin.String")) {
                            +irCall(rbBodyFn).apply {
                                arguments[0] = irGet(rb)
                                arguments[1] = irGet(param)
                            }
                        } else {
                            // Symmetric with return types: serialize @Body via kotlinx.serialization
                            val bodySerializer = resolveSerializer(this, bodyType)
                            +irCall(rbBodyJsonFn).apply {
                                arguments[0] = irGet(rb)
                                arguments[1] = irGet(param)
                                arguments[2] = bodySerializer
                                typeArguments[0] = bodyType
                            }
                        }
                    }
                    fieldAnn != null -> +irCall(rbFieldFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irString(constStringArg(fieldAnn))
                        arguments[2] = irGet(param)
                    }
                    fieldMapAnn != null -> +irCall(rbFieldMapFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irGet(param)
                    }
                    partAnn != null -> +irCall(rbPartFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irString(constStringArg(partAnn))
                        arguments[2] = irString(constStringArgAt(partAnn, 1) ?: "")
                        arguments[3] = irGet(param)
                    }
                    urlAnn != null -> +irCall(rbUseUrlFn).apply {
                        arguments[0] = irGet(rb)
                        arguments[1] = irGet(param)
                    }
                }
            }
            +irReturn(buildExecuteCall(rb, fn))
        }
    }

    private fun IrBlockBodyBuilder.buildExecuteCall(
        rb: IrVariable,
        fn: IrSimpleFunction,
    ): IrExpression {
        val returnType = fn.returnType
        val stringFqn = FqName("kotlin.String")
        val responseFqn = FqName("io.ktorkit.Response")
        val flowFqn = FqName("kotlinx.coroutines.flow.Flow")

        if (returnType.classFqName == flowFqn && returnType is IrSimpleType) {
            val elem = returnType.arguments.firstOrNull()?.typeOrNull
                ?: error("ktorkit: Flow<T> missing type argument")
            return if (elem.classFqName == stringFqn) {
                irCall(rbExecuteAsFlowOfStringFn).apply { arguments[0] = irGet(rb) }
            } else {
                val ser = resolveSerializer(this, elem)
                irCall(rbExecuteAsFlowFn).apply {
                    arguments[0] = irGet(rb)
                    arguments[1] = ser
                    typeArguments[0] = elem
                }
            }
        }

        if (returnType.classFqName == responseFqn && returnType is IrSimpleType) {
            val inner = returnType.arguments.firstOrNull()?.typeOrNull
                ?: error("ktorkit: Response<T> missing type argument")
            return if (inner.classFqName == stringFqn) {
                irCall(rbExecuteAsResponseStringFn).apply { arguments[0] = irGet(rb) }
            } else {
                val ser = resolveSerializer(this, inner)
                irCall(rbExecuteAsResponseWithDeserializerFn).apply {
                    arguments[0] = irGet(rb)
                    arguments[1] = ser
                    typeArguments[0] = inner
                }
            }
        }

        if (returnType.classFqName == stringFqn) {
            return irCall(rbExecuteFn).apply { arguments[0] = irGet(rb) }
        }

        val serializerCall = resolveSerializer(this, returnType)
        return irCall(rbExecuteWithDeserializerFn).apply {
            arguments[0] = irGet(rb)
            arguments[1] = serializerCall
            typeArguments[0] = returnType
        }
    }

    private fun IrBuilderWithScope.resolveSerializer(
        scope: IrBuilderWithScope,
        type: IrType,
    ): IrExpression {
        if (type.isMarkedNullable()) {
            val baseType = type.makeNotNull()
            val baseSerializer = resolveSerializer(scope, baseType)
            // `nullable` is an extension property: arguments[0] is its receiver.
            return scope.irCall(nullableGetter).apply {
                arguments[0] = baseSerializer
                typeArguments[0] = baseType
            }
        }
        val fqn = type.classFqName?.asString()
        if (fqn == "kotlin.collections.List" && type is IrSimpleType) {
            val elemType = type.arguments.firstOrNull()?.typeOrNull
                ?: error("ktorkit: List<T> return type missing element type")
            val elemSerializer = resolveSerializer(scope, elemType)
            return scope.irCall(listSerializerFn).apply {
                arguments[0] = elemSerializer
                typeArguments[0] = elemType
            }
        }
        if (fqn == "kotlin.collections.Map" && type is IrSimpleType) {
            val keyType = type.arguments.getOrNull(0)?.typeOrNull
                ?: error("ktorkit: Map<K, V> missing key type")
            val valueType = type.arguments.getOrNull(1)?.typeOrNull
                ?: error("ktorkit: Map<K, V> missing value type")
            val keySer = resolveSerializer(scope, keyType)
            val valSer = resolveSerializer(scope, valueType)
            return scope.irCall(mapSerializerFn).apply {
                arguments[0] = keySer
                arguments[1] = valSer
                typeArguments[0] = keyType
                typeArguments[1] = valueType
            }
        }
        // Built-in primitives (String, Int, Long, Boolean, ...) — extension fn on Companion
        primitiveSerializerCall(scope, type, fqn)?.let { return it }
        val notSerializable =
            "ktorkit: cannot build a serializer for '${fqn ?: type.render()}'. " +
                "Annotate the type with @Serializable and apply the " +
                "kotlinx-serialization compiler plugin."
        val classSym = type.classOrNull ?: return scope.failWith(notSerializable)
        val companion = classSym.owner.companionObject() ?: return scope.failWith(notSerializable)
        val serializerFn = companion.functions.firstOrNull {
            it.name.asString() == "serializer" && it.regularParameters.isEmpty()
        } ?: return scope.failWith(notSerializable)
        return scope.irCall(serializerFn.symbol).apply {
            arguments[0] = scope.irGetObject(companion.symbol)
        }
    }

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

    private fun primitiveSerializerCall(
        scope: IrBuilderWithScope,
        type: IrType,
        fqn: String?,
    ): IrExpression? {
        if (fqn == null) return null
        val classSym = type.classOrNull ?: return null
        val companion = classSym.owner.companionObject() ?: return null
        val match = builtinsPrimitiveSerializers.firstOrNull { fn ->
            fn.owner.parameters
                .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
                ?.type?.classFqName?.asString() == "$fqn.Companion"
        } ?: return null
        return scope.irCall(match).apply {
            arguments[0] = scope.irGetObject(companion.symbol)
        }
    }

    private fun interfaceBaseUrlOf(fn: IrSimpleFunction): String? {
        // fn lives on Impl; Impl is nested in the interface
        val implClass = fn.parent as? IrClass ?: return null
        val ifaceClass = implClass.parent as? IrClass ?: return null
        val ann = ifaceClass.getAnnotation(ktorkitPkg.child(Name.identifier("ContributesAPI")))
            ?: return null
        val raw = (ann.arguments.getOrNull(0) as? IrConst)?.value as? String ?: return null
        return raw.takeIf { it.isNotEmpty() }
    }

    private fun isFormEncoded(fn: IrSimpleFunction): Boolean {
        val candidates = listOf(fn) + fn.overriddenSymbols.map { it.owner }
        return candidates.any { it.getAnnotation(ktorkitPkg.child(Name.identifier("FormUrlEncoded"))) != null }
    }

    private fun isMultipart(fn: IrSimpleFunction): Boolean {
        val candidates = listOf(fn) + fn.overriddenSymbols.map { it.owner }
        return candidates.any { it.getAnnotation(ktorkitPkg.child(Name.identifier("Multipart"))) != null }
    }

    private fun staticHeadersOf(fn: IrSimpleFunction): List<Pair<String, String>> {
        val candidates = listOf(fn) + fn.overriddenSymbols.map { it.owner }
        for (candidate in candidates) {
            val ann = candidate.getAnnotation(ktorkitPkg.child(Name.identifier("Headers"))) ?: continue
            val vararg = ann.arguments.getOrNull(0) as? IrVararg ?: return emptyList()
            return vararg.elements.mapNotNull { el ->
                val raw = (el as? IrConst)?.value as? String ?: return@mapNotNull null
                val colonIdx = raw.indexOf(':')
                if (colonIdx <= 0) return@mapNotNull null
                raw.substring(0, colonIdx).trim() to raw.substring(colonIdx + 1).trim()
            }
        }
        return emptyList()
    }

    private fun httpMethodOf(fn: IrSimpleFunction): Pair<String, String>? {
        val candidates = listOf(fn) + fn.overriddenSymbols.map { it.owner }
        for (candidate in candidates) {
            for (v in httpVerbs) {
                val ann = candidate.getAnnotation(ktorkitPkg.child(Name.identifier(v))) ?: continue
                return v to constStringArg(ann)
            }
        }
        return null
    }

    /**
     * Annotations live on the interface declaration, not on the generated override,
     * so fall back through [IrSimpleFunction.overriddenSymbols] by parameter position.
     */
    private fun paramAnnotation(
        param: IrValueParameter,
        fn: IrSimpleFunction,
        fqName: FqName,
    ): IrConstructorCall? {
        param.getAnnotation(fqName)?.let { return it }
        val idx = fn.regularParameters.indexOf(param)
        if (idx < 0) return null
        for (overridden in fn.overriddenSymbols) {
            val overriddenParams = overridden.owner.regularParameters
            if (idx < overriddenParams.size) {
                overriddenParams[idx].getAnnotation(fqName)?.let { return it }
            }
        }
        return null
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
