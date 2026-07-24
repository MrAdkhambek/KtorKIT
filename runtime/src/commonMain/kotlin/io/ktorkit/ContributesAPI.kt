package io.ktorkit

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ContributesAPI(val baseUrl: String = "")
