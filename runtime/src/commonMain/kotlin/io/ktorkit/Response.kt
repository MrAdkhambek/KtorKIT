package io.ktorkit

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode

class Response<T>(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: T,
) {
    val isSuccess: Boolean get() = status.value in 200..299
}
