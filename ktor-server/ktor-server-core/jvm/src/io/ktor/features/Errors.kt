package io.ktor.features

import io.ktor.http.*
import io.ktor.util.*
import java.lang.Exception
import kotlin.reflect.*

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
class MissingRequestParameterException(val parameterName: String) :
    BadRequestException("Request parameter $parameterName is missing")

/**
 * This exception is thrown when a required parameter with name [parameterName] couldn't be converted to the [type]
 * @property parameterName of missing request parameter
 * @property type this parameter is unable to convert to
 */
@KtorExperimentalAPI
class ParameterConversionException(val parameterName: String, val type: String, cause: Throwable? = null) :
    BadRequestException("Request parameter $parameterName couldn't be parsed/converted to $type", cause)

/**
 * Thrown when content cannot be transformed to the desired type.
 * It is not defined which status code will be replied when an exception of this type is thrown and not caught.
 * Depending on child type it could be 4xx or 5xx status code. By default it will be 500 Internal Server Error.
 */
@KtorExperimentalAPI
abstract class ContentTransformationException(message: String) : Exception(message)

internal class CannotTransformContentToTypeException(type: KClass<*>) :
    ContentTransformationException("Cannot transform this request's content to $type")

/**
 * Thrown when there is no conversion for a content type configured.
 * HTTP status 415 Unsupported Media Type will be replied when this exception is thrown and not caught.
 */
class UnsupportedMediaTypeException(contentType: ContentType) :
    ContentTransformationException("Content type $contentType is not supported")
