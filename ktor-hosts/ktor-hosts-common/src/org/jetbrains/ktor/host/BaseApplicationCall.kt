package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    override val execution = PipelineMachine()

    final override val attributes = Attributes()

    override fun respond(message: Any): Nothing {
        val state = ResponsePipelineState(this, message)
        execution.execute(state, respondPipeline)
    }

    protected fun commit(o: HostResponse) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, values) in o.headers.entries()) {
            for (value in values) {
                response.header(name, value)
            }
        }
    }

    protected val respondPipeline = RespondPipeline()
    protected val HostRespondPhase = PipelinePhase("HostRespondPhase")
    protected val HostRespondFinalizationPhase = PipelinePhase("HostRespondFinalizationPhase")

    private var finalizeAction: (PipelineContext<*>) -> Nothing = { context ->
        context.pause()
    }

    init {
        respondPipeline.phases.insertAfter(RespondPipeline.After, HostRespondPhase)
        respondPipeline.phases.insertAfter(HostRespondPhase, HostRespondFinalizationPhase)

        respondPipeline.intercept(HostRespondPhase) { state ->
            val message = state.message
            when (message) {
                is FinalContent -> handleFinalContent(message)
                is ProtocolUpgrade -> handleUpgrade(message)
            }
        }

        respondPipeline.intercept(HostRespondPhase) { state ->
            val value = state.message

            when (value) {
                is StreamConsumer -> {
                    val pipe = ChannelPipe()
                    closeAtEnd(pipe)

                    // note: it is very important to resend it here rather than just handle right here
                    // because we need compression, ranges and etc to work properly
                    respond(PipeResponse(pipe, { value.headers }, start = {
                        finalizeAction = { context ->
                            try {
                                value.stream(pipe.asOutputStream())
                            } catch (ignore: ChannelPipe.PipeClosedException) {
                            } catch (t: Throwable) {
                                pipe.rethrow(t)
                            } finally {
                                pipe.closeAndWait()
                            }

                            context.pause()
                        }
                    }))
                }
            }
        }
    }

    open fun PipelineContext<*>.handleFinalContent(content: FinalContent) {
        commit(content)

        when (content) {
            is FinalContent.NoContent -> {
                close()
                finishAll()
            }
            is FinalContent.ChannelContent -> {
                sendAsyncChannel { content.channel() }
            }
            is FinalContent.StreamContentProvider -> {
                sendAsyncChannel { content.stream().asAsyncChannel() }
            }
        }
    }

    protected abstract fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade)
    protected abstract fun responseChannel(): WriteChannel
    protected open val pool: ByteBufferPool
        get() = NoPool

    protected fun PipelineContext<*>.sendAsyncChannel(channelProvider: () -> ReadChannel): Nothing {
        // note: it is important to open response channel before we open content channel
        // otherwise we can hit deadlock on event-based hosts

        val response = responseChannel()
        val channel = channelProvider()

        closeAtEnd(channel, this@BaseApplicationCall) // TODO closeAtEnd(call) should be done globally at call start
        channel.copyToAsync(response, writeCompletionHandler(this), ignoreWriteError = true, alloc = pool)

        finalizeAction(this)
    }

    override val parameters: ValuesMap by lazy { request.queryParameters + request.content.get() }

    private class PipeResponse(val pipe: ChannelPipe, headersDelegate: () -> ValuesMap, val start: () -> Unit) : FinalContent.ChannelContent() {
        override val headers by lazy(headersDelegate)

        override fun channel(): ReadChannel {
            start()
            return pipe
        }
    }

    private fun writeCompletionHandler(pipelineContext: PipelineContext<*>) = { failure: Throwable? ->
        pipelineContext.runBlockWithResult {
            pipelineContext.handleThrowable(failure)
        }
        Unit
    }

    private fun PipelineContext<*>.handleThrowable(failure: Throwable?) {
        if (failure == null || failure is PipelineControl.Continue || failure.cause is PipelineControl.Continue) {
            finishAll()
        } else if (failure !is PipelineControl && failure.cause !is PipelineControl) {
            fail(failure)
        }
    }
}