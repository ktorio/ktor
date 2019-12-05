/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * Base exception to indicate that the request is not correct due to
 * wrong/missing request parameters, body content or header values.
 * Throwing this exception in a handler will lead to 400 Bad Request response
 * unless a custom [io.ktor.features.StatusPages] handler registered.
 */
@KtorExperimentalAPI
open class BadRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * This exception means that the requested resource is not found.
 * HTTP status 404 Not found will be replied when this exception is thrown and not caught.
 * 404 status page could be configured by registering a custom [io.ktor.features.StatusPages] handler.
 */
@KtorExperimentalAPI
class NotFoundException(message: String? = "Resource not found") : Exception(message)

/**
 * This exception is thrown when a required parameter with name [parameterName] is missing
 * @property parameterName of missing request parameter
 */
@KtorExperimentalAPI
class MissingRequestParameterException(
    val parameterName: String
) : BadRequestException("Request parameter $parameterName is missing"),
    CopyableThrowable<MissingRequestParameterException> {

    override fun createCopy(): MissingRequestParameterException = MissingRequestParameterException(parameterName).also {
        it.initCause(this)
    }
}

/**
 * This exception is thrown when a required parameter with name [parameterName] couldn't be converted to the [type]
 * @property parameterName of missing request parameter
 * @property type this parameter is unable to convert to
 */
@KtorExperimentalAPI
class ParameterConversionException(val parameterName: String, val type: String, cause: Throwable? = null) :
    BadRequestException("Request parameter $parameterName couldn't be parsed/converted to $type", cause),
    CopyableThrowable<ParameterConversionException> {

    override fun createCopy(): ParameterConversionException =
        ParameterConversionException(parameterName, type, this).also {
            it.initCause(this)
        }
}

/**
 * Thrown when content cannot be transformed to the desired type.
 * It is not defined which status code will be replied when an exception of this type is thrown and not caught.
 * Depending on child type it could be 4xx or 5xx status code. By default it will be 500 Internal Server Error.
 */
@KtorExperimentalAPI
abstract class ContentTransformationException(message: String) : Exception(message)

internal class CannotTransformContentToTypeException(
    private val type: KType
) : ContentTransformationException("Cannot transform this request's content to $type"),
    CopyableThrowable<CannotTransformContentToTypeException> {
    @Suppress("unused")
    @Deprecated("Use KType instead", level = DeprecationLevel.HIDDEN)
    constructor(type: KClass<*>) : this(type.starProjectedType)

    override fun createCopy(): CannotTransformContentToTypeException? =
        CannotTransformContentToTypeException(type).also {
            it.initCause(this)
        }
}

/**
 * Thrown when there is no conversion for a content type configured.
 * HTTP status 415 Unsupported Media Type will be replied when this exception is thrown and not caught.
 */
class UnsupportedMediaTypeException(
    private val contentType: ContentType
) : ContentTransformationException("Content type $contentType is not supported"),
    CopyableThrowable<UnsupportedMediaTypeException> {

    override fun createCopy(): UnsupportedMediaTypeException? = UnsupportedMediaTypeException(contentType).also {
        it.initCause(this)
    }
}
