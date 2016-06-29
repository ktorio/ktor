package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

abstract class BaseApplicationCall(override val application: Application, override val executor: Executor) : ApplicationCall {
    val executionMachine = PipelineMachine()
    final override val attributes = Attributes()

    override fun execute(pipeline: Pipeline<ApplicationCall>): PipelineState {
        try {
            executionMachine.execute(this, pipeline)
        } catch (e: PipelineControlFlow) {
            when (e) {
                is PipelineCompleted -> return PipelineState.Succeeded
                is PipelinePaused -> return PipelineState.Executing
                else -> throw e
            }
        }
    }

    override fun <T : Any> fork(value: T, pipeline: Pipeline<T>): Nothing = executionMachine.execute(value, pipeline)
    override fun respond(message: Any): Nothing {
        val state = ResponsePipelineState(this, message)
        executionMachine.execute(state, respond)
    }

    protected fun commit(o: FinalContent) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, value) in o.headers.flattenEntries()) {
            response.header(name, value)
        }
    }

    final override val respond = RespondPipeline()
    private val HostRespondPhase = PipelinePhase("HostRespondPhase")

    init {
        respond.phases.insertAfter(RespondPipeline.After, HostRespondPhase)

        respond.intercept(HostRespondPhase) { state ->
            val value = state.message

            when (value) {
                is FinalContent.StreamConsumer -> {
                    val pipe = ChannelPipe()
                    closeAtEnd(pipe)

                    // note: it is very important to resend it here rather than just use value.startContent
                    respond(PipeResponse(pipe, { value.headers }) {
                        executor.execute {
                            try {
                                value.stream(pipe.asOutputStream())
                            } catch (ignore: ChannelPipe.PipeClosedException) {
                            } finally {
                                pipe.closeAndWait()
                            }
                        }
                    })
                }
                is FinalContent.ProtocolUpgrade -> {
                    commit(value)
                    value.upgrade(this@BaseApplicationCall, this, request.content.get(), response.channel())
                    pause()
                }
                is FinalContent -> {
                    commit(value)
                    value.startContent(this@BaseApplicationCall, this)
                }
            }
        }
    }

    private class PipeResponse(val pipe: ChannelPipe, headersDelegate: () -> ValuesMap, val start: () -> Unit) : FinalContent.ChannelContent() {
        override val headers by lazy(headersDelegate)

        override fun channel(): ReadChannel {
            start()
            return pipe
        }
    }


    companion object {
        val ResponseChannelOverride = AttributeKey<WriteChannel>("ktor.response.channel")
        val RequestChannelOverride = AttributeKey<ReadChannel>("ktor.request.channel")
    }
}