package io.ktorkit.compiler

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId

object KtorKitDeclarationCheckers : DeclarationCheckers() {
    override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(ContributesApiOnInterfaceChecker)
    override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> =
        setOf(HttpVerbChecker, PathPlaceholderChecker, FormEncodingChecker)
}

private val CONTRIBUTES_API_ID = KtorKitNames.CONTRIBUTES_API_ID
private val HTTP_VERB_IDS = KtorKitNames.HTTP_VERB_IDS
private val PATH_ID = KtorKitNames.PATH_ID
private val FIELD_ID = KtorKitNames.FIELD_ID
private val FIELD_MAP_ID = KtorKitNames.FIELD_MAP_ID
private val FORM_URL_ENCODED_ID = KtorKitNames.FORM_URL_ENCODED_ID
private val PART_ID = KtorKitNames.PART_ID
private val MULTIPART_ID = KtorKitNames.MULTIPART_ID
private val BODY_ID = KtorKitNames.BODY_ID
private val PLACEHOLDER_REGEX = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)}""")

private fun FirAnnotationContainer.findAnnotation(classId: ClassId): FirAnnotation? =
    annotations.firstOrNull { it.annotationTypeRef.coneType.classId == classId }

private fun FirAnnotationContainer.hasAnnotation(classId: ClassId): Boolean =
    findAnnotation(classId) != null

object ContributesApiOnInterfaceChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (!declaration.hasAnnotation(CONTRIBUTES_API_ID)) return
        if (declaration.classKind != ClassKind.INTERFACE) {
            reporter.reportOn(declaration.source, KtorKitErrors.CONTRIBUTES_API_NOT_INTERFACE)
        }
    }
}

/**
 * `CheckerContext.containingDeclarations` holds symbols (not declarations) as of K2 2.1,
 * so resolve back to the FIR node before inspecting annotations.
 */
@OptIn(SymbolInternals::class)
private fun parentIsContributesApi(context: CheckerContext): Boolean {
    val parent = (context.containingDeclarations.lastOrNull() as? FirRegularClassSymbol)?.fir
        ?: return false
    return parent.hasAnnotation(CONTRIBUTES_API_ID) && parent.classKind == ClassKind.INTERFACE
}

object HttpVerbChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        if (!parentIsContributesApi(context)) return
        val verbCount = HTTP_VERB_IDS.count { declaration.hasAnnotation(it) }
        when {
            verbCount == 0 ->
                reporter.reportOn(declaration.source, KtorKitErrors.MISSING_HTTP_VERB)
            verbCount > 1 ->
                reporter.reportOn(declaration.source, KtorKitErrors.MULTIPLE_HTTP_VERBS)
        }
    }
}

object PathPlaceholderChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        if (!parentIsContributesApi(context)) return
        val verbAnn = HTTP_VERB_IDS.firstNotNullOfOrNull { declaration.findAnnotation(it) } ?: return
        val pathTemplate = constStringArg(verbAnn) ?: return
        val placeholders = PLACEHOLDER_REGEX.findAll(pathTemplate).map { it.groupValues[1] }.toSet()

        val pathParamNames = mutableSetOf<String>()
        for (param in declaration.valueParameters) {
            val pathAnn = param.findAnnotation(PATH_ID) ?: continue
            val name = constStringArg(pathAnn) ?: continue
            pathParamNames += name
            if (name !in placeholders) {
                reporter.reportOn(
                    param.source,
                    KtorKitErrors.PATH_PARAM_NOT_IN_TEMPLATE,
                    name,
                )
            }
        }
        for (placeholder in placeholders) {
            if (placeholder !in pathParamNames) {
                reporter.reportOn(
                    declaration.source,
                    KtorKitErrors.PLACEHOLDER_HAS_NO_PARAM,
                    placeholder,
                )
            }
        }
    }
}

object FormEncodingChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        if (!parentIsContributesApi(context)) return

        val isForm = declaration.hasAnnotation(FORM_URL_ENCODED_ID)
        val isMultipart = declaration.hasAnnotation(MULTIPART_ID)
        if (isForm && isMultipart) {
            reporter.reportOn(declaration.source, KtorKitErrors.CONFLICTING_BODY_ENCODING)
            return
        }

        for (param in declaration.valueParameters) {
            if (!isForm && (param.hasAnnotation(FIELD_ID) || param.hasAnnotation(FIELD_MAP_ID))) {
                reporter.reportOn(param.source, KtorKitErrors.FORM_PARAM_WITHOUT_FORM_ENCODED)
            }
            if (!isMultipart && param.hasAnnotation(PART_ID)) {
                reporter.reportOn(param.source, KtorKitErrors.PART_WITHOUT_MULTIPART)
            }
            // An encoded form wins over requestBody in RequestBuilder.configureRequest(),
            // so a @Body alongside one would never reach the wire.
            if ((isForm || isMultipart) && param.hasAnnotation(BODY_ID)) {
                reporter.reportOn(param.source, KtorKitErrors.BODY_WITH_ENCODED_FORM)
            }
        }
    }
}

private fun constStringArg(annotation: FirAnnotation): String? {
    val expr = annotation.argumentMapping.mapping.values.firstOrNull() ?: return null
    return (expr as? FirLiteralExpression)?.value as? String
}
