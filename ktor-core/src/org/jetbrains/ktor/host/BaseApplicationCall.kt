package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    override val execution = PipelineMachine()

    final override val attributes = Attributes()

    override fun respond(message: Any): Nothing {
        val state = ResponsePipelineState(this, message)
        execution.execute(state, respondPipeline)
    }

    protected fun commit(o: FinalContent) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, value) in o.headers.flattenEntries()) {
            response.header(name, value)
        }
    }

    protected val respondPipeline = RespondPipeline()

    private val HostRespondPhase = PipelinePhase("HostRespondPhase")

    init {
        respondPipeline.phases.insertAfter(RespondPipeline.After, HostRespondPhase)

        respondPipeline.intercept(HostRespondPhase) { state ->
            val value = state.message

            when (value) {
                is FinalContent.StreamConsumer -> {
                    val pipe = ChannelPipe()
                    closeAtEnd(pipe)

                    // note: it is very important to resend it here rather than just use value.startContent
                    respond(PipeResponse(pipe, { value.headers }) {
                        application.executor.execute {
                            try {
                                value.stream(pipe.asOutputStream())
                            } catch (ignore: ChannelPipe.PipeClosedException) {
                            } catch (t: Throwable) {
                                pipe.rethrow(t)
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

    override val parameters: ValuesMap by lazy { request.queryParameters + request.content.get() }

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