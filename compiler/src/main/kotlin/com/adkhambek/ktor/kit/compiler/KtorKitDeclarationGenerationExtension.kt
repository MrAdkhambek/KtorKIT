package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.resolve.providers.getRegularClassSymbolByClassId
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class KtorKitDeclarationGenerationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val implName = Name.identifier("Impl")
    private val clientParamName = Name.identifier("client")
    private val ktorClientClassId = ClassId(FqName("com.adkhambek.ktor.kit"), Name.identifier("KtorClient"))

    private val contributesApiPredicate =
        LookupPredicate.BuilderContext.annotated(FqName("com.adkhambek.ktor.kit.ContributesAPI"))

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(contributesApiPredicate)
    }

    private fun isContributesApi(symbol: FirClassSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(contributesApiPredicate, symbol)

    @OptIn(SymbolInternals::class)
    private fun hasUserDeclaredImpl(owner: FirClassSymbol<*>): Boolean {
        val regular = owner as? FirRegularClassSymbol ?: return false
        return regular.fir.declarations
            .filterIsInstance<FirRegularClass>()
            .any { it.name == implName }
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        if (!isContributesApi(classSymbol)) return emptySet()
        if (hasUserDeclaredImpl(classSymbol)) return emptySet()
        return setOf(implName)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (name != implName) return null
        if (!isContributesApi(owner)) return null
        if (hasUserDeclaredImpl(owner)) return null

        val ifaceType = owner.classId.constructClassLikeType(emptyArray(), false)
        val newClass = createNestedClass(
            owner = owner,
            name = implName,
            key = KtorKitPluginKey,
            classKind = ClassKind.CLASS,
        ) {
            modality = Modality.FINAL
            visibility = Visibilities.Public
            superType(ifaceType)
        }
        return newClass.symbol
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        if (!isOurGeneratedImpl(classSymbol)) return emptySet()
        val ifaceFunctionNames = ownerInterfaceOf(classSymbol)
            ?.declarationSymbols
            ?.filterIsInstance<FirNamedFunctionSymbol>()
            ?.filter { isAbstractFunction(it) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        return ifaceFunctionNames + SpecialNames.INIT
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val owner = context.owner
        if (!isOurGeneratedImpl(owner)) return emptyList()

        val ktorClientType = ktorClientClassId.constructClassLikeType(emptyArray(), false)
        val ctor = createConstructor(
            owner = owner,
            key = KtorKitPluginKey,
            isPrimary = true,
        ) {
            valueParameter(clientParamName, ktorClientType)
        }
        return listOf(ctor.symbol)
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        if (!isOurGeneratedImpl(owner)) return emptyList()
        val iface = ownerInterfaceOf(owner) ?: return emptyList()

        val abstractFn = iface.declarationSymbols
            .filterIsInstance<FirNamedFunctionSymbol>()
            .firstOrNull { it.name == callableId.callableName && isAbstractFunction(it) }
            ?: return emptyList()

        val isSuspendFn = isSuspendFunction(abstractFn)
        val fn = createMemberFunction(
            owner = owner,
            key = KtorKitPluginKey,
            name = abstractFn.name,
            returnType = abstractFn.resolvedReturnType,
        ) {
            modality = Modality.FINAL
            visibility = Visibilities.Public
            for (p in abstractFn.valueParameterSymbols) {
                valueParameter(p.name, p.resolvedReturnType)
            }
            status {
                isOverride = true
                isSuspend = isSuspendFn
            }
        }
        return listOf(fn.symbol)
    }

    @OptIn(SymbolInternals::class)
    private fun isAbstractFunction(symbol: FirNamedFunctionSymbol): Boolean = symbol.fir.isAbstract

    @OptIn(SymbolInternals::class)
    private fun isSuspendFunction(symbol: FirNamedFunctionSymbol): Boolean = symbol.fir.isSuspend

    private fun isOurGeneratedImpl(classSymbol: FirClassSymbol<*>): Boolean {
        if (classSymbol.classId.shortClassName != implName) return false
        val outerId = classSymbol.classId.outerClassId ?: return false
        val outerSymbol = session.getRegularClassSymbolByClassId(outerId) ?: return false
        return isContributesApi(outerSymbol) && !hasUserDeclaredImpl(outerSymbol)
    }

    private fun ownerInterfaceOf(implSymbol: FirClassSymbol<*>): FirRegularClassSymbol? {
        val outerId = implSymbol.classId.outerClassId ?: return null
        return session.getRegularClassSymbolByClassId(outerId)
    }
}
