package com.adkhambek.ktor.kit

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ContributesAPI(val baseUrl: String = "")
