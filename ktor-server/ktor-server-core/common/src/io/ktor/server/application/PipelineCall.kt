/*
* Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.CoroutineScope

private val RECEIVE_TYPE_KEY: AttributeKey<TypeInfo> = AttributeKey("ReceiveType")
private val RECEIVE_GUARD_KEY: AttributeKey<ReceiveGuard> = AttributeKey("ReceiveGuard")

/**
 * Rejects overlapping receive operations for a call: nested ones started from
 * an [ApplicationReceivePipeline] interceptor and concurrent ones.
 */
internal class ReceiveGuard {
    private val inProgress = atomic(false)

    fun tryAcquire(): Boolean = inProgress.compareAndSet(expect = false, update = true)

    fun release() {
        inProgress.value = false
    }
}

private fun PipelineCall.receiveGuard(): ReceiveGuard =
    when (this) {
        is BaseApplicationCall -> receiveGuard

        is RoutingPipelineCall -> engineCall.receiveGuard()

        // Call attributes aren't thread-safe, so concurrent receives are detected on a best-effort basis here.
        else -> attributes.computeIfAbsent(RECEIVE_GUARD_KEY) { ReceiveGuard() }
    }

/**
 * A single act of communication between a client and server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall)
 *
 * @see [io.ktor.server.request.ApplicationRequest]
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public interface ApplicationCall : CoroutineScope {
    /**
     * [Attributes] attached to this call.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.attributes)
     */
    public val attributes: Attributes

    /**
     * An [ApplicationRequest] that is a client request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.request)
     */
    public val request: ApplicationRequest

    /**
     * An [PipelineResponse] that is a server response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.response)
     */
    public val response: ApplicationResponse

    /**
     * An application being called.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.application)
     */
    public val application: Application

    /**
     * Parameters associated with this call.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.parameters)
     */
    public val parameters: Parameters

    /**
     * Receives content for this request according to [typeInfo].
     *
     * This function returns `null` only when [TypeInfo.isNullable] is `true`.
     * The caller is responsible for ensuring that [typeInfo] represents the same type as [T].
     * Receive operations for the same call must be sequential and must not be started from
     * an [ApplicationReceivePipeline] interceptor.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.receive)
     *
     * @param typeInfo instance specifying the type to be received.
     * @return an instance of [T] received from this call.
     * @throws ContentTransformationException when content cannot be transformed to the requested type.
     * @throws IllegalStateException when another receive operation is in progress for this call.
     */
    public suspend fun <T> receive(typeInfo: TypeInfo): T {
        @Suppress("DEPRECATION")
        val result = receiveNullable<T>(typeInfo)
        if (!typeInfo.isNullable && result == null) throw CannotTransformContentToTypeException(typeInfo)

        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Receives content for this request.
     * Receive operations for the same call must be sequential and must not be started from
     * an [ApplicationReceivePipeline] interceptor.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.receiveNullable)
     *
     * @param typeInfo instance specifying type to be received.
     * @return instance of [T] received from this call.
     * @throws ContentTransformationException when content cannot be transformed to the requested type.
     * @throws IllegalStateException when another receive operation is in progress for this call.
     */
    @Deprecated("Use 'receive<T>(typeInfo)' with nullable T instead", ReplaceWith("receive<T?>(typeInfo)"))
    public suspend fun <T> receiveNullable(typeInfo: TypeInfo): T?

    /**
     * Sends a [message] as a response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCall.respond)
     *
     * @see [io.ktor.server.response.PipelineResponse]
     */
    public suspend fun respond(message: Any?, typeInfo: TypeInfo?)
}

/**
 * A single act of communication between a client and server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.PipelineCall)
 *
 * @see [io.ktor.server.request.PipelineRequest]
 * @see [io.ktor.server.response.PipelineResponse]
 */
public interface PipelineCall : ApplicationCall {

    /**
     * An [PipelineRequest] that is a client request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.PipelineCall.request)
     */
    public override val request: PipelineRequest

    /**
     * An [PipelineResponse] that is a server response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.PipelineCall.response)
     */
    public override val response: PipelineResponse

    @Deprecated(
        "Use 'receive<T>(typeInfo)' with nullable T instead",
        replaceWith = ReplaceWith("receive<T?>(typeInfo)")
    )
    public override suspend fun <T> receiveNullable(typeInfo: TypeInfo): T? {
        val guard = receiveGuard()
        check(guard.tryAcquire()) {
            "The request body is already being received for this call. Receive operations must be sequential " +
                "and must not be started from an ApplicationReceivePipeline interceptor."
        }
        try {
            val token = attributes.getOrNull(DoubleReceivePreventionTokenKey)
            if (token == null) {
                attributes.put(DoubleReceivePreventionTokenKey, DoubleReceivePreventionToken)
            }

            receiveType = typeInfo
            val incomingContent = token ?: request.receiveChannel()
            val transformed = request.pipeline.execute(this, incomingContent)
            when {
                transformed == NullBody -> return null
                transformed === DoubleReceivePreventionToken -> throw RequestAlreadyConsumedException()
                !typeInfo.type.isInstance(transformed) -> throw CannotTransformContentToTypeException(typeInfo)
            }

            @Suppress("UNCHECKED_CAST")
            return transformed as T
        } finally {
            guard.release()
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun respond(message: Any?, typeInfo: TypeInfo?) {
        response.responseType = typeInfo
        response.pipeline.execute(this, message ?: NullBody)
    }
}

/**
 * Indicates if a response is sent.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.isHandled)
 */
public val ApplicationCall.isHandled: Boolean get() = response.isCommitted

/**
 * The [TypeInfo] recorded from the last [call.receive<Type>()] call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.receiveType)
 */
public var ApplicationCall.receiveType: TypeInfo
    get() = attributes[RECEIVE_TYPE_KEY]
    internal set(value) {
        attributes.put(RECEIVE_TYPE_KEY, value)
    }

/**
 * Convenience extension property for pipeline interceptors with Application call contexts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.call)
 */
public val <C : ApplicationCall> PipelineContext<*, C>.call: C get() = context
