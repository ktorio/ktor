/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.concurrent.*
import kotlin.jvm.*
import kotlin.native.concurrent.*

@SharedImmutable
private val ValidateMark = AttributeKey<Unit>("ValidateMark")

/**
 * Default response validation.
 * Check the response status code in range (0..299).
 */
public fun HttpClientConfig<*>.addDefaultResponseValidation() {
    HttpResponseValidator {
        @Suppress("DEPRECATION")
        expectSuccess = this@addDefaultResponseValidation.expectSuccess

        validateResponse { response ->
            val expectSuccess = response.call.attributes[ExpectSuccessAttributeKey]
            if (!expectSuccess) {
                return@validateResponse
            }

            val statusCode = response.status.value
            val originCall = response.call
            if (statusCode < 300 || originCall.attributes.contains(ValidateMark)) {
                return@validateResponse
            }

            val exceptionCall = originCall.save().apply {
                attributes.put(ValidateMark, Unit)
            }

            val exceptionResponse = exceptionCall.response
            val exceptionResponseText = exceptionResponse.readText()
            when (statusCode) {
                in 300..399 -> throw RedirectResponseException(exceptionResponse, exceptionResponseText)
                in 400..499 -> throw ClientRequestException(exceptionResponse, exceptionResponseText)
                in 500..599 -> throw ServerResponseException(exceptionResponse, exceptionResponseText)
                else -> throw ResponseException(exceptionResponse, exceptionResponseText)
            }
        }
    }
}

internal const val NO_RESPONSE_TEXT: String = "<no response text provided>"
internal const val DEPRECATED_EXCEPTION_CTOR: String = "Please, provide response text in constructor"

/**
 * Base for default response exceptions.
 * @param [response]: origin response
 */
public open class ResponseException(
    response: HttpResponse,
    cachedResponseText: String
) : IllegalStateException("Bad response: $response. Text: \"$cachedResponseText\"") {
    @Deprecated(level = DeprecationLevel.WARNING, message = DEPRECATED_EXCEPTION_CTOR)
    public constructor(response: HttpResponse) : this(response, NO_RESPONSE_TEXT)

    @delegate:Transient
    private val _response: HttpResponse? by threadLocal(response)
    public val response: HttpResponse
        get() = _response ?: error("Failed to access response from a different native thread")
}

/**
 * Unhandled redirect exception.
 */
@Suppress("KDocMissingDocumentation")
public class RedirectResponseException(response: HttpResponse, cachedResponseText: String) :
    ResponseException(response, cachedResponseText) {
    @Deprecated(level = DeprecationLevel.WARNING, message = DEPRECATED_EXCEPTION_CTOR)
    public constructor(response: HttpResponse) : this(response, NO_RESPONSE_TEXT)

    override val message: String? = "Unhandled redirect: ${response.call.request.url}. " +
        "Status: ${response.status}. Text: \"$cachedResponseText\""
}

/**
 * Server error exception.
 */
@Suppress("KDocMissingDocumentation")
public class ServerResponseException(
    response: HttpResponse,
    cachedResponseText: String
) : ResponseException(response, cachedResponseText) {
    @Deprecated(level = DeprecationLevel.WARNING, message = DEPRECATED_EXCEPTION_CTOR)
    public constructor(response: HttpResponse) : this(response, NO_RESPONSE_TEXT)

    override val message: String? = "Server error(${response.call.request.url}: " +
        "${response.status}. Text: \"$cachedResponseText\""
}

/**
 * Bad client request exception.
 */
@Suppress("KDocMissingDocumentation")
public class ClientRequestException(
    response: HttpResponse,
    cachedResponseText: String
) : ResponseException(response, cachedResponseText) {
    @Deprecated(level = DeprecationLevel.WARNING, message = DEPRECATED_EXCEPTION_CTOR)
    public constructor(response: HttpResponse) : this(response, NO_RESPONSE_TEXT)

    override val message: String = "Client request(${response.call.request.url}) " +
        "invalid: ${response.status}. Text: \"$cachedResponseText\""
}
