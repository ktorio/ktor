/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.features.DoubleReceive.*
import io.ktor.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.*

/**
 * This feature provides ability to invoke [ApplicationCall.receive] several times.
 * Please note that not every type could be received twice. For example, even with this feature installed you can't
 * receive a channel twice (unless [Configuration.receiveEntireContent] is enabled).
 * Types that always can be received twice or more: `ByteArray`, `String` and `Parameters`.
 * Also some of content transformation features (such as [ContentNegotiation]) could support it as well.
 * If not specified, a transformation result is not considered as reusable. So a transformation feature may
 * mark a result as reusable by proceeding with a [ApplicationReceiveRequest] instance having
 * [ApplicationReceiveRequest.reusableValue] `= true`.
 * So installing [DoubleReceive] with [ContentNegotiation] provides ability to receive a user type that will be
 * deserialized at first receive and then the same instance will be returned for every further receive invocation.
 * When the same receive type requested as the firstly received, the receive pipeline and content transformation are
 * not triggered (except when [Configuration.receiveEntireContent] = `true`).
 */
@KtorExperimentalAPI
class DoubleReceive internal constructor(private val config: Configuration) {
    /**
     * [DoubleReceive] Feature configuration.
     */
    class Configuration {

        /**
         * When enabled, for every request the whole content will be received and stored as a byte array.
         * This is useful when completely different types need to be received.
         * You also can receive streams and channels.
         * Note that enabling this causes the whole receive pipeline to be executed for every further receive pipeline.
         */
        var receiveEntireContent: Boolean = false
    }

    /**
     * [DoubleReceive] feature's installation object.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    companion object Feature : ApplicationFeature<Application, Configuration, DoubleReceive> {
        override val key: AttributeKey<DoubleReceive> = AttributeKey("DoubleReceive")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): DoubleReceive {
            val feature = DoubleReceive(Configuration().apply(configure))

            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) { request ->
                val type = request.typeInfo
                require(request.type != CachedTransformationResult::class) { "CachedTransformationResult can't be received" }

                val cachedResult = call.attributes.getOrNull(LastReceiveCachedResult)
                when {
                    cachedResult == null -> call.attributes.put(LastReceiveCachedResult, RequestAlreadyConsumedResult)
                    cachedResult === RequestAlreadyConsumedResult -> throw RequestAlreadyConsumedException()
                    cachedResult is CachedTransformationResult.Failure -> throw RequestReceiveAlreadyFailedException(
                        cachedResult.cause
                    )
                    cachedResult is CachedTransformationResult.Success<*> && cachedResult.type == type -> {
                        proceedWith(ApplicationReceiveRequest(request.typeInfo, cachedResult.value))
                        return@intercept
                    }
                }

                var byteArray = (cachedResult as? CachedTransformationResult.Success<*>)?.value as? ByteArray
                val requestValue = request.value

                if (byteArray == null && feature.config.receiveEntireContent && requestValue is ByteReadChannel) {
                    byteArray = requestValue.toByteArray()
                    call.attributes.put(
                        LastReceiveCachedResult,
                        CachedTransformationResult.Success(typeOf<ByteArray>(), byteArray)
                    )
                }

                val incomingContent = byteArray?.let { ByteReadChannel(it) } ?: cachedResult ?: request.value
                val finishedRequest = try {
                    proceedWith(ApplicationReceiveRequest(type, incomingContent))
                } catch (cause: Throwable) {
                    call.attributes.put(LastReceiveCachedResult, CachedTransformationResult.Failure(type, cause))
                    throw cause
                }

                val transformed = finishedRequest.value

                when {
                    transformed is CachedTransformationResult.Success<*> -> throw RequestAlreadyConsumedException()
                    !request.type.isInstance(transformed) -> throw CannotTransformContentToTypeException(type)
                }

                if (finishedRequest.reusableValue && (cachedResult == null || cachedResult !is CachedTransformationResult.Success)) {
                    @Suppress("UNCHECKED_CAST")
                    call.attributes.put(
                        LastReceiveCachedResult,
                        CachedTransformationResult.Success(type, finishedRequest.value)
                    )
                }
            }

            return feature
        }
    }
}

/**
 * Represents a cached transformation result from a previous [ApplicationCall.receive] invocation.
 * @property type requested by the corresponding [ApplicationCall.receive] invocation
 */
@KtorExperimentalAPI
sealed class CachedTransformationResult<T : Any>(val type: KType) {
    /**
     * Holds a transformation result [value] after a successful transformation.
     * @property value
     */
    class Success<T : Any>(type: KType, val value: T) : CachedTransformationResult<T>(type)

    /**
     * Holds a transformation failure [cause]
     * @property cause describes transformation failure
     */
    open class Failure(type: KType, val cause: Throwable) : CachedTransformationResult<Nothing>(type)
}

/**
 * Thrown when a request receive was failed during the previous [ApplicationCall.receive] invocation so this
 * receive attempt is simply replaying the previous exception cause.
 */
@KtorExperimentalAPI
class RequestReceiveAlreadyFailedException internal constructor(
    cause: Throwable
) : Exception("Request body consumption was failed", cause, false, true)

private val LastReceiveCachedResult = AttributeKey<CachedTransformationResult<*>>("LastReceiveRequest")

/**
 * It is assigned to a call when request pipeline is running or completed with no reusable value.
 * For example, if a stream is received, one is unable to receive any values after that. However, when received
 * a text, this instance will be replaced with the corresponding cached receive request.
 */
@UseExperimental(ExperimentalStdlibApi::class)
private val RequestAlreadyConsumedResult =
    CachedTransformationResult.Failure(typeOf<Any>(), RequestAlreadyConsumedException())
