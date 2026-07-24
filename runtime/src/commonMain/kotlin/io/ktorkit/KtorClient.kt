package io.ktorkit

import io.ktor.client.HttpClient

class KtorClient(
    val baseUrl: String,
    val httpClient: HttpClient,
)
