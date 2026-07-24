package com.adkhambek.ktor.kit

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class Response<T>(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: T,
) {
    val isSuccess: Boolean get() = status.isSuccess()
}
