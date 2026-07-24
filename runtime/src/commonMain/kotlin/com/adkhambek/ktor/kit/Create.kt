package com.adkhambek.ktor.kit

inline fun <reified T : Any> KtorClient.create(): T =
    error("KtorKit compiler plugin not applied — call site of create<${T::class.simpleName}>() was not rewritten")
