package com.adkhambek.ktor.kit.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Single source of truth for the `com.adkhambek.ktor.kit` vocabulary.
 *
 * The FIR and IR phases need the same names in different forms — [ClassId] for FIR
 * checkers, [FqName] for IR annotation lookups — so both are derived here rather than
 * re-spelled per phase. Adding an annotation or an HTTP verb is a one-line change.
 *
 * These are also constructed once per compilation instead of per declaration:
 * `FqName.child(Name.identifier(..))` allocates on every call, and the IR generator
 * previously rebuilt ten of them for every parameter of every generated function.
 */
internal object KtorKitNames {

    val PKG = FqName("com.adkhambek.ktor.kit")

    val HTTP_VERBS = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    /** Verb name paired with its annotation [FqName], for the IR phase. */
    val HTTP_VERB_FQ_NAMES: List<Pair<String, FqName>> = HTTP_VERBS.map { it to fqName(it) }

    /** Verb annotation ids, for FIR checkers. */
    val HTTP_VERB_IDS: List<ClassId> = HTTP_VERBS.map { classId(it) }

    // Parameter annotations
    val PATH = fqName("Path")
    val QUERY = fqName("Query")
    val HEADER = fqName("Header")
    val HEADER_MAP = fqName("HeaderMap")
    val QUERY_MAP = fqName("QueryMap")
    val BODY = fqName("Body")
    val FIELD = fqName("Field")
    val FIELD_MAP = fqName("FieldMap")
    val PART = fqName("Part")
    val URL = fqName("Url")

    // Function / class annotations
    val HEADERS = fqName("Headers")
    val FORM_URL_ENCODED = fqName("FormUrlEncoded")
    val MULTIPART = fqName("Multipart")
    val CONTRIBUTES_API = fqName("ContributesAPI")

    // FIR-side ids
    val CONTRIBUTES_API_ID = classId("ContributesAPI")
    val PATH_ID = classId("Path")
    val FIELD_ID = classId("Field")
    val FIELD_MAP_ID = classId("FieldMap")
    val FORM_URL_ENCODED_ID = classId("FormUrlEncoded")
    val PART_ID = classId("Part")
    val MULTIPART_ID = classId("Multipart")
    val BODY_ID = classId("Body")

    /**
     * Every annotation that binds a parameter to some part of the request. A parameter
     * must carry exactly one: none means the argument is silently never sent, and more
     * than one means the winner depends on an arbitrary internal ordering.
     */
    val PARAM_BINDING_IDS: List<ClassId> = listOf(
        "Path", "Query", "QueryMap", "Header", "HeaderMap",
        "Body", "Field", "FieldMap", "Part", "Url",
    ).map { classId(it) }

    // Runtime types the generated code targets
    val KTOR_CLIENT_ID = classId("KtorClient")
    val REQUEST_BUILDER_ID = classId("RequestBuilder")
    val RESPONSE_FQ_NAME = fqName("Response")

    val CREATE_FQ_NAME = fqName("create")

    // Types the return-type dispatch keys off
    val STRING_FQ_NAME = FqName("kotlin.String")
    val UNIT_FQ_NAME = FqName("kotlin.Unit")
    val FLOW_FQ_NAME = FqName("kotlinx.coroutines.flow.Flow")
    val LIST_FQ_NAME = FqName("kotlin.collections.List")
    val MAP_FQ_NAME = FqName("kotlin.collections.Map")

    private fun fqName(simpleName: String) = PKG.child(Name.identifier(simpleName))
    private fun classId(simpleName: String) = ClassId(PKG, Name.identifier(simpleName))
}
