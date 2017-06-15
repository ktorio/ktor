package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

/**
 * Represents client's request
 */
interface ApplicationRequest {
    /**
     * [ApplicationCall] instance this ApplicationRequest is attached to
     */
    val call: ApplicationCall

    /**
     * Pipeline for transforming request content into receive objects
     */
    val pipeline: ApplicationReceivePipeline

    /**
     * Parameters provided in an URL
     */
    val queryParameters: ValuesMap

    /**
     * Headers for this request
     */
    val headers: ValuesMap

    /**
     * Contains http request and connection details such as a host name used to connect, port, scheme and so on.
     * No proxy headers could affect it. Use [ApplicationRequest.origin] if you need override headers support
     */
    val local: RequestConnectionPoint

    /**
     * Cookies for this request
     */
    val cookies: RequestCookies

    /**
     * Receive content for this request
     */
    suspend fun <T : Any> tryReceive(type: KClass<T>): T?
}

inline suspend fun <reified T : Any> ApplicationRequest.tryReceive(): T? = tryReceive(T::class)
inline suspend fun <reified T : Any> ApplicationRequest.receive(): T {
    val type = T::class
    return tryReceive(type) ?: throw Exception("Cannot transform this request's content into $type")
}

@Deprecated("Replace 'content.get<>()' with 'receive<>'", level = DeprecationLevel.ERROR)
val ApplicationRequest.content: Any get() = TODO()

open class ApplicationReceivePipeline : Pipeline<ApplicationReceiveRequest>(Before, Transform, After) {
    companion object RespondPhase {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val After = PipelinePhase("After")
    }
}

class ApplicationReceiveRequest(val call: ApplicationCall, val type: KClass<*>, val value: Any)
