package io.ktor.client.features

@Deprecated(
    "[BadResponseStatusException] is deprecated. Use [ResponseException] instead.",
    ReplaceWith("ResponseException"),
    DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation")
typealias BadResponseStatusException = ResponseException
