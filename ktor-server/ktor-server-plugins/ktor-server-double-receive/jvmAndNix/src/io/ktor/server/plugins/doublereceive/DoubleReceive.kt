/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.doublereceive.DoubleReceive.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * This plugin provides ability to invoke [ApplicationCall.receive] several times.
 * Please note that not every type could be received twice. For example, even with this plugin installed you can't
 * receive a channel twice (unless [Configuration.receiveEntireContent] is enabled).
 * Types that always can be received twice or more: `ByteArray`, `String` and `Parameters`.
 * Also some of content transformation plugins (such as [ContentNegotiation]) could support it as well.
 * If not specified, a transformation result is not considered as reusable. So a transformation plugin may
 * mark a result as reusable by proceeding with a [ApplicationReceiveRequest] instance having
 * [ApplicationReceiveRequest.reusableValue] `= true`.
 * So installing [DoubleReceive] with [ContentNegotiation] provides ability to receive a user type that will be
 * deserialized at first receive and then the same instance will be returned for every further receive invocation.
 * When the same receive type requested as the firstly received, the receive pipeline and content transformation are
 * not triggered (except when [Configuration.receiveEntireContent] = `true`).
 */
public class DoubleReceive internal constructor(private val config: Configuration) {
    /**
     * [DoubleReceive] Plugin configuration.
     */
    public class Configuration {

        /**
         * When enabled, for every request the whole content will be received and stored as a byte array.
         * This is useful when completely different types need to be received.
         * You also can receive streams and channels.
         * Note that enabling this causes the whole receive pipeline to be executed for every further receive pipeline.
         */
        public var receiveEntireContent: Boolean = false
    }

    /**
     * [DoubleReceive] plugin's installation object.
     */
    public companion object Plugin : ApplicationPlugin<Application, Configuration, DoubleReceive> {
        override val key: AttributeKey<DoubleReceive> = AttributeKey("DoubleReceive")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): DoubleReceive {
            val plugin = DoubleReceive(Configuration().apply(configure))

            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) { request ->
                require(request.typeInfo.type != CachedTransformationResult::class) {
                    "CachedTransformationResult can't be received"
                }

                val cache = call.attributes.getCache()
                if (cache is CachedTransformationResult.Success<*> && cache.type == request.typeInfo) {
                    proceedWith(ApplicationReceiveRequest(cache.type, cache.value))
                    return@intercept
                }

                if (cache == null) {
                    call.attributes.putCache(RequestAlreadyConsumedResult)
                }

                cache.checkAlreadyConsumedOrFailure()

                val shouldReceiveEntirely = plugin.config.receiveEntireContent && request.value is ByteReadChannel &&
                    !cache.isByteArray()

                val bodyBytes = if (shouldReceiveEntirely) {
                    (request.value as ByteReadChannel).toByteArray()
                } else {
                    (cache as? CachedTransformationResult.Success<*>)?.value as? ByteArray
                }

                if (shouldReceiveEntirely) {
                    @OptIn(ExperimentalStdlibApi::class)
                    call.attributes.putSuccessfulCache(typeInfo<ByteArray>(), bodyBytes as ByteArray)
                }

                val result = try {
                    val value = bodyBytes?.let { ByteReadChannel(it) } ?: cache ?: request.value
                    proceedWith(ApplicationReceiveRequest(request.typeInfo, value))
                } catch (cause: Throwable) {
                    call.attributes.putFailureCache(request.typeInfo, cause)
                    throw cause
                }

                result.checkAlreadyConsumedOrWrongType(request.typeInfo)

                if (result.reusableValue && cache.isNothingOrFailure()) {
                    call.attributes.putSuccessfulCache(request.typeInfo, result.value)
                }
            }

            return plugin
        }
    }
}

private fun CachedTransformationResult<*>?.checkAlreadyConsumedOrFailure() {
    when {
        this === RequestAlreadyConsumedResult -> throw RequestAlreadyConsumedException()
        this is CachedTransformationResult.Failure -> throw RequestReceiveAlreadyFailedException(this.cause)
    }
}

private fun CachedTransformationResult<*>?.isByteArray(): Boolean {
    return (this is CachedTransformationResult.Success<*> && this.value is ByteArray)
}

private fun ApplicationReceiveRequest.checkAlreadyConsumedOrWrongType(requestType: TypeInfo) {
    when {
        value is CachedTransformationResult.Success<*> -> throw RequestAlreadyConsumedException()
        !requestType.type.isInstance(value) -> throw CannotTransformContentToTypeException(requestType.kotlinType!!)
    }
}

private fun CachedTransformationResult<*>?.isNothingOrFailure(): Boolean {
    return this == null || this !is CachedTransformationResult.Success
}

private fun <T : Any> Attributes.putSuccessfulCache(type: TypeInfo, value: T) {
    putCache(CachedTransformationResult.Success(type, value))
}

private fun Attributes.putFailureCache(type: TypeInfo, cause: Throwable) {
    putCache(CachedTransformationResult.Failure(type, cause))
}

private fun Attributes.putCache(value: CachedTransformationResult<*>) {
    put(LastReceiveCachedResult, value)
}

private fun Attributes.getCache(): CachedTransformationResult<*>? {
    return getOrNull(LastReceiveCachedResult)
}

/**
 * Represents a cached transformation result from a previous [ApplicationCall.receive] invocation.
 * @property type requested by the corresponding [ApplicationCall.receive] invocation
 */
public sealed class CachedTransformationResult<T : Any>(public val type: TypeInfo) {
    /**
     * Holds a transformation result [value] after a successful transformation.
     * @property value
     */
    public class Success<T : Any>(type: TypeInfo, public val value: T) : CachedTransformationResult<T>(type)

    /**
     * Holds a transformation failure [cause]
     * @property cause describes transformation failure
     */
    public open class Failure(type: TypeInfo, public val cause: Throwable) : CachedTransformationResult<Nothing>(type)
}

/**
 * Thrown when a request receive was failed during the previous [ApplicationCall.receive] invocation so this
 * receive attempt is simply replaying the previous exception cause.
 */
public class RequestReceiveAlreadyFailedException internal constructor(
    cause: Throwable
) : Exception("Request body consumption was failed", cause)

private val LastReceiveCachedResult = AttributeKey<CachedTransformationResult<*>>("LastReceiveRequest")

/**
 * It is assigned to a call when request pipeline is running or completed with no reusable value.
 * For example, if a stream is received, one is unable to receive any values after that. However, when received
 * a text, this instance will be replaced with the corresponding cached receive request.
 */
@OptIn(ExperimentalStdlibApi::class)
private val RequestAlreadyConsumedResult =
    CachedTransformationResult.Failure(typeInfo<Any>(), RequestAlreadyConsumedException())
