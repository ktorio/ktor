package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.concurrent.*

/**
 * Represents a single act of communication between client and server.
 */
interface ApplicationCall : Closeable {
    /**
     * Application being called
     */
    val application: Application

    /**
     * Application executor you can execute tasks on via [runAsync]
     */
    val executor: Executor

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
    override fun close(): Unit

    val parameters: ValuesMap

    fun respond(message: Any): Nothing

    val respond: RespondPipeline

    val transform: TransformTable<PipelineContext<ResponsePipelineState>>

    fun interceptRespond(phase: PipelinePhase, handler: PipelineContext<ResponsePipelineState>.(Any) -> Unit) {
        respond.intercept(phase) {
            handler(it.obj)
        }
    }

    fun <T : Any> fork(value: T, pipeline: Pipeline<T>): Nothing

    fun execute(pipeline: Pipeline<ApplicationCall>): PipelineState
}
