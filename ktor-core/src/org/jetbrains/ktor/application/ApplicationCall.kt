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
     *
     * Can be intercepted to perform any cleanup tasks that are required by the processing pipeline
     */
    val close: Interceptable0<Unit>

    val parameters: ValuesMap

    fun respond(message: Any): Nothing

    fun interceptRespond(handler: PipelineContext<Any>.(Any) -> Unit)

    fun <T : Any> fork(pipeline: Pipeline<T>, value: T,
                       attach: (PipelineExecution<*>, PipelineExecution<T>) -> Unit,
                       detach: (PipelineExecution<*>, PipelineExecution<T>) -> Unit): Nothing

    fun <T : Any> execute(pipeline: Pipeline<T>, value: T): PipelineExecution.State
}

/**
 * Closes this call and sends out any remaining data
 */
fun ApplicationCall.close(): Unit = close.execute()
