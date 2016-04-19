package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

/**
 * Represents a single act of communication between client and server.
 */
interface ApplicationCall {
    /**
     * Application being called
     */
    val application: Application

    /**
     * Client request
     */
    val request: ApplicationRequest

    /**
     * Server response
     */
    val response: ApplicationResponse

    /**
     * Attributes attached to this instance
     */
    val attributes: Attributes

    /**
     * Closes this call and sends out any remaining data
     */
    fun close(): Unit

    val parameters: ValuesMap

    fun respond(message: Any): Nothing

    fun interceptRespond(handler: PipelineContext<Any>.(Any) -> Unit)
    fun interceptRespond(index: Int, handler: PipelineContext<Any>.(Any) -> Unit)

    fun <T : Any> fork(value: T, pipeline: Pipeline<T>): Nothing

    fun execute(pipeline: Pipeline<ApplicationCall>): PipelineState
}
