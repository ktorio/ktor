package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import java.io.*
import java.util.concurrent.*

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    val executionMachine = PipelineMachine()

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
    override fun respond(message: Any): Nothing = fork(message, respond)

    override fun interceptRespond(handler: PipelineContext<Any>.(Any) -> Unit) = respond.intercept(handler)

    protected fun sendHeaders(value: Any) {
        if (value is Resource) {
            value.cacheControl?.let { cacheControl ->
                response.cacheControl(cacheControl)
            }
            value.expires?.let { expires ->
                response.expires(expires)
            }
            value.contentLength?.let { length ->
                response.contentLength(length)
            }
            value.versions.forEach { version ->
                version.render(response)
            }
            response.contentType(value.contentType)
            return
        }

        if (value is HasContentLength) {
            response.contentLength(value.contentLength)
        }
    }

    private val respond = Pipeline<Any>()

    init {
        respond.intercept { value ->
            sendHeaders(value)
            when (value) {
                is String -> {
                    response.status() ?: response.status(HttpStatusCode.OK)
                    val encoding = response.headers[HttpHeaders.ContentType]?.let {
                        ContentType.parse(it).parameter("charset")
                    } ?: "UTF-8"
                    response.streamText(value, encoding)
                    close()
                }
                is TextContent -> {
                    response.contentType(value.contentType)
                    respond(value.text)
                }
                is HttpStatusContent -> {
                    response.status(value.code)
                    respond(TextContent(ContentType.Text.Html, "<H1>${value.code}</H1>${value.message}"))
                }
                is HttpStatusCode -> {
                    response.status(value)
                    close()
                }
                is StreamContent -> {
                    response.status() ?: response.status(HttpStatusCode.OK)
                    response.stream {
                        value.stream(this)
                    }
                    close()
                }
                is URIFileContent -> {
                    if (value.uri.scheme == "file") {
                        respond(LocalFileContent(File(value.uri)))
                    } else {
                        response.status() ?: response.status(HttpStatusCode.OK)
                        sendStream(value.stream())
                    }
                }
                is ChannelContentProvider -> {
                    response.status() ?: response.status(HttpStatusCode.OK)
                    sendAsyncChannel(value.channel())
                }
                is StreamContentProvider -> {
                    response.status() ?: response.status(HttpStatusCode.OK)
                    sendStream(value.stream())
                }
            }
        }
    }

    private fun PipelineContext<*>.sendAsyncChannel(channel: AsyncReadChannel): Nothing {
        val future = createMachineCompletableFuture()

        closeAtEnd()
        channel.copyToAsyncThenComplete(response.channel(), future)
        pause()
    }

    protected fun PipelineContext<*>.sendStream(stream: InputStream): Nothing {
        val future = createMachineCompletableFuture()

        closeAtEnd()
        InputStreamReadChannelAdapter(stream).copyToAsyncThenComplete(response.channel(), future)
        pause()
    }

    protected fun PipelineContext<*>.closeAtEnd() {
        onSuccess {
            close()
        }
        onFail {
            close()
        }
    }

    private fun PipelineContext<*>.createMachineCompletableFuture() = CompletableFuture<Long>().apply {
        whenComplete { total, throwable ->
            if (throwable == null || throwable is PipelineContinue || throwable.cause is PipelineContinue) {
                proceed()
            }
            else if (throwable !is PipelineControlFlow && throwable.cause !is PipelineControlFlow) {
                fail(throwable)
            }
        }
    }
}