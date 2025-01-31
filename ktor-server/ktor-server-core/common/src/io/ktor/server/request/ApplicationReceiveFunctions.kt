/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.internal.*
import io.ktor.server.plugins.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.reflect.*

private val FORM_FIELD_LIMIT = AttributeKey<Long>("FormFieldLimit")

@PublishedApi
internal expect val DEFAULT_FORM_FIELD_LIMIT: Long

/**
 * A pipeline for processing incoming content.
 * When executed, this pipeline starts with an instance of [ByteReadChannel].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationReceivePipeline)
 */
public open class ApplicationReceivePipeline(
    override val developmentMode: Boolean = false
) : Pipeline<Any, PipelineCall>(Before, Transform, After) {
    /**
     * Pipeline phases.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationReceivePipeline.Phases)
     */
    @Suppress("PublicApiImplicitType")
    public companion object Phases {
        /**
         * Executes before any transformations are made.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationReceivePipeline.Phases.Before)
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Executes transformations.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationReceivePipeline.Phases.Transform)
         */
        public val Transform: PipelinePhase = PipelinePhase("Transform")

        /**
         * Executes after all transformations.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationReceivePipeline.Phases.After)
         */
        public val After: PipelinePhase = PipelinePhase("After")
    }
}

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveOrNull)
 *
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type.
 */
@Deprecated(
    "receiveOrNull is ambiguous with receiveNullable and going to be removed in 3.0.0. " +
        "Please consider replacing it with runCatching with receive or receiveNullable",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("kotlin.runCatching { this.receiveNullable<T>() }.getOrNull()")
)
@Suppress("DEPRECATION_ERROR")
public suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = receiveOrNull(typeInfo<T>())

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receive)
 *
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
public suspend inline fun <reified T : Any> ApplicationCall.receive(): T = receiveNullable(typeInfo<T>())
    ?: throw CannotTransformContentToTypeException(typeInfo<T>().kotlinType!!)

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveNullable)
 *
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
public suspend inline fun <reified T> ApplicationCall.receiveNullable(): T? = receiveNullable(typeInfo<T>())

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receive)
 *
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
public suspend fun <T : Any> ApplicationCall.receive(type: KClass<T>): T {
    val kotlinType = starProjectedTypeBridge(type)
    return receiveNullable(TypeInfo(type, kotlinType))!!
}

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receive)
 *
 * @param typeInfo instance specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 * @throws NullPointerException when content is `null`.
 */
public suspend fun <T> ApplicationCall.receive(typeInfo: TypeInfo): T = receiveNullable(typeInfo)!!

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveOrNull)
 *
 * @param [typeInfo] type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type.
 */
@Deprecated(
    "receiveOrNull is ambiguous with receiveNullable and going to be removed in 3.0.0. " +
        "Please consider replacing it with runCatching with receive or receiveNullable",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("kotlin.runCatching { this.receiveNullable<T>() }.getOrNull()")
)
public suspend fun <T : Any> ApplicationCall.receiveOrNull(typeInfo: TypeInfo): T? {
    return try {
        receiveNullable(typeInfo)
    } catch (cause: ContentTransformationException) {
        application.log.debug("Conversion failed, null returned", cause)
        null
    }
}

/**
 * Receives content for this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveOrNull)
 *
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
@Deprecated(
    "receiveOrNull is ambiguous with receiveNullable and going to be removed in 3.0.0. " +
        "Please consider replacing it with runCatching with receive or receiveNullable",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("kotlin.runCatching { this.receiveNullable<T>() }.getOrNull()")
)
public suspend fun <T : Any> ApplicationCall.receiveOrNull(type: KClass<T>): T? = try {
    receive(type)
} catch (cause: ContentTransformationException) {
    application.log.debug("Conversion failed, null returned", cause)
    null
}

/**
 * Receives incoming content for this call as [String].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveText)
 *
 * @return text received from this call.
 * @throws BadRequestException when Content-Type header is invalid.
 */
public suspend inline fun ApplicationCall.receiveText(): String {
    val charset = try {
        request.contentCharset() ?: Charsets.UTF_8
    } catch (cause: BadContentTypeFormatException) {
        throw BadRequestException("Illegal Content-Type format: ${request.headers[HttpHeaders.ContentType]}", cause)
    }
    return receiveChannel().readRemaining().readText(charset)
}

/**
 * Receives channel content for this call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveChannel)
 *
 * @return instance of [ByteReadChannel] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [ByteReadChannel].
 */
public suspend inline fun ApplicationCall.receiveChannel(): ByteReadChannel = receive()

/**
 * Represents the limit for form field size in bytes for an [ApplicationCall].
 * This limit determines the maximum size allowed for form field data in a request.
 *
 * The default value is 65536 bytes (64 KB).
 *
 * To get the value of the formFieldLimit, use the getter:
 * ```
 * val limit = call.formFieldLimit
 * ```
 *
 * To set the value of the formFieldLimit, use the setter:
 * ```
 * call.formFieldLimit = limit
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.formFieldLimit)
 */
public var ApplicationCall.formFieldLimit: Long
    get() {
        return attributes.getOrNull(FORM_FIELD_LIMIT) ?: DEFAULT_FORM_FIELD_LIMIT
    }
    set(value) {
        attributes.put(FORM_FIELD_LIMIT, value)
    }

/**
 * Receives multipart data for this call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveMultipart)
 *
 * @return instance of [MultiPartData].
 * @throws ContentTransformationException when content cannot be transformed to the [MultiPartData].
 */
public suspend inline fun ApplicationCall.receiveMultipart(
    formFieldLimit: Long = -1L
): MultiPartData {
    if (formFieldLimit > 0) {
        this.formFieldLimit = formFieldLimit
    }
    return receive()
}

/**
 * Receives form parameters for this call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveParameters)
 *
 * @return instance of [Parameters].
 * @throws ContentTransformationException when content cannot be transformed to the [Parameters].
 */
public suspend inline fun ApplicationCall.receiveParameters(): Parameters = receive()

/**
 * Thrown when content cannot be transformed to the desired type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ContentTransformationException)
 */
public typealias ContentTransformationException = io.ktor.server.plugins.ContentTransformationException

/**
 * This object is attached to an [ApplicationCall] with [DoubleReceivePreventionTokenKey] when
 * the [receive] function is invoked. It is used to detect double receive invocation
 * that causes [RequestAlreadyConsumedException] to be thrown unless the [DoubleReceive] plugin installed.
 */
internal object DoubleReceivePreventionToken

internal val DoubleReceivePreventionTokenKey =
    AttributeKey<DoubleReceivePreventionToken>("DoubleReceivePreventionToken")

/**
 * Thrown when a request body has already been received.
 * Usually it is caused by double [ApplicationCall.receive] invocation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.RequestAlreadyConsumedException)
 */
public class RequestAlreadyConsumedException : IllegalStateException(
    "Request body has already been consumed (received)."
)
