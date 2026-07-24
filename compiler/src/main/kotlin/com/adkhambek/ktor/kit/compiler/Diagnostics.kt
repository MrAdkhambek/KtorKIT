package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

object KtorKitErrors : KtDiagnosticsContainer() {

    val CONTRIBUTES_API_NOT_INTERFACE: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME,
    )

    val MISSING_HTTP_VERB: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME,
    )

    val MULTIPLE_HTTP_VERBS: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME,
    )

    val PATH_PARAM_NOT_IN_TEMPLATE: KtDiagnosticFactory1<String> by error1<KtElement, String>(
        SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val PLACEHOLDER_HAS_NO_PARAM: KtDiagnosticFactory1<String> by error1<KtElement, String>(
        SourceElementPositioningStrategies.DECLARATION_NAME,
    )

    val FORM_PARAM_WITHOUT_FORM_ENCODED: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val PART_WITHOUT_MULTIPART: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val CONFLICTING_BODY_ENCODING: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME,
    )

    val BODY_WITH_ENCODED_FORM: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val MISSING_PARAM_BINDING: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val CONFLICTING_PARAM_BINDING: KtDiagnosticFactory0 by error0<KtElement>(
        SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = KtorKitDefaultErrorMessages
}

object KtorKitDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("KtorKit") { map ->
        map.put(
            KtorKitErrors.CONTRIBUTES_API_NOT_INTERFACE,
            "@ContributesAPI may only be applied to an interface."
        )
        map.put(
            KtorKitErrors.MISSING_HTTP_VERB,
            "Function in @ContributesAPI interface must declare exactly one HTTP verb annotation (@GET / @POST / @PUT / @DELETE / @PATCH / @HEAD / @OPTIONS)."
        )
        map.put(
            KtorKitErrors.MULTIPLE_HTTP_VERBS,
            "Function declares more than one HTTP verb annotation; pick one."
        )
        map.put(
            KtorKitErrors.PATH_PARAM_NOT_IN_TEMPLATE,
            "@Path(\"{0}\") has no matching '{'{0}'}' placeholder in the URL template.",
            CommonRenderers.STRING,
        )
        map.put(
            KtorKitErrors.PLACEHOLDER_HAS_NO_PARAM,
            "URL template contains '{'{0}'}' but no @Path(\"{0}\") parameter is declared.",
            CommonRenderers.STRING,
        )
        map.put(
            KtorKitErrors.FORM_PARAM_WITHOUT_FORM_ENCODED,
            "@Field/@FieldMap requires the function to be annotated @FormUrlEncoded; " +
                "without it the form values are silently dropped."
        )
        map.put(
            KtorKitErrors.PART_WITHOUT_MULTIPART,
            "@Part requires the function to be annotated @Multipart; " +
                "without it the parts are silently dropped."
        )
        map.put(
            KtorKitErrors.CONFLICTING_BODY_ENCODING,
            "Function declares both @FormUrlEncoded and @Multipart; pick one body encoding."
        )
        map.put(
            KtorKitErrors.BODY_WITH_ENCODED_FORM,
            "@Body cannot be combined with @FormUrlEncoded or @Multipart; " +
                "the encoded form takes precedence and the body would be silently dropped."
        )
        map.put(
            KtorKitErrors.MISSING_PARAM_BINDING,
            "Parameter has no KtorKit annotation, so its value would never be sent. " +
                "Annotate it with one of @Path / @Query / @QueryMap / @Header / @HeaderMap / " +
                "@Body / @Field / @FieldMap / @Part / @Url."
        )
        map.put(
            KtorKitErrors.CONFLICTING_PARAM_BINDING,
            "Parameter declares more than one KtorKit binding annotation; pick one."
        )
    }
}
