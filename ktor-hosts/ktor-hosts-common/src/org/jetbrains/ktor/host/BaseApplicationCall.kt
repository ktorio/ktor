package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.atomic.*
import kotlinx.support.jdk7.addSuppressed

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()

    suspend override fun respond(message: Any) {
        val state = ResponsePipelineState(this, message)
        respondPipeline.execute(state)
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

    private var contentProducer: (PipelineContext<*>) -> Unit = { context ->
        context.producerComplete(null)
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
                    respond(PipeResponse(pipe, value.status, { value.headers }, onChannelOpen = {
                        // it is important to only set a function reference
                        contentProducer = { context ->
                            val failure = try {
                                value.stream(pipe.asOutputStream())
                                pipe.closeAndWait()
                                null
                            } catch (ignore: ChannelPipe.PipeClosedException) {
                                null
                            } catch (t: Throwable) {
                                pipe.rethrow(t)
                                t
                            }

                            context.producerComplete(failure)
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

    protected fun PipelineContext<*>.sendAsyncChannel(channelProvider: () -> ReadChannel) {
        // note: it is important to open response channel before we open content channel
        // otherwise we can hit deadlock on event-based hosts

        val response = responseChannel()
        val channel = channelProvider()

        //closeAtEnd(channel, this@BaseApplicationCall) // TODO closeAtEnd(call) should be done globally at call start
        channel.copyToAsync(response, pumpCompletionHandler(this), ignoreWriteError = true, alloc = pool)

        contentProducer(this)
    }

    override val parameters: ValuesMap by lazy { request.queryParameters + request.content.get() }

    private class PipeResponse(val pipe: ChannelPipe,
                               override val status: HttpStatusCode?,
                               headersDelegate: () -> ValuesMap,
                               val onChannelOpen: () -> Unit) : FinalContent.ChannelContent() {
        override val headers by lazy(headersDelegate)

        override fun channel(): ReadChannel {
            onChannelOpen()
            return pipe
        }
    }

    private var producerFailure: Throwable? = null
    private val producerCompleted = AtomicBoolean(false)

    private var pumpFailure: Throwable? = null
    private val pumpCompleted = AtomicBoolean(false)

    private val completionCounter = AtomicInteger(2) // 2 = pump and producer

    private fun PipelineContext<*>.producerComplete(failure: Throwable?) {
        if (producerCompleted.compareAndSet(false, true)) {
            producerFailure = failure
            tryComplete()
        }
    }

    private fun pumpCompletionHandler(pipelineContext: PipelineContext<*>) = { failure: Throwable? ->
        if (pumpCompleted.compareAndSet(false, true)) {
            pumpFailure = failure
            pipelineContext.tryComplete()
        }
    }

    private fun PipelineContext<*>.tryComplete() {
        if (completionCounter.decrementAndGet() == 0) {
                doComplete()
        }
    }

    // we get here only when both producer and pump completed. Only once so here we should choose exception
    // and proceed pipeline (possibly previously suspended)
    private fun PipelineContext<*>.doComplete() {
        val failure = when {
            pumpFailure != null && producerFailure != null -> if (pumpFailure !== producerFailure) {
                producerFailure!!.addSuppressed(pumpFailure!!)
                producerFailure
            } else {
                producerFailure
            }

            else -> producerFailure ?: pumpFailure
        }
    }
}