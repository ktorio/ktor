package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import java.io.*
import java.nio.charset.*
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
    override fun interceptRespond(index: Int, handler: PipelineContext<Any>.(Any) -> Unit) = respond.intercept(index, handler)

    protected fun sendHeaders(value: Any) {
        if (value is HasVersions) {
            value.versions.forEach { version ->
                version.render(response)
            }
        }

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
            response.contentType(value.contentType)
            return
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

                    ifNotHead {
                        val bytes = value.toByteArray(Charset.forName(encoding))
                        respond(object : ChannelContentProvider {
                            override fun channel(): AsyncReadChannel = ByteArrayAsyncReadChannel(bytes)
                        })
                    }
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
                    finishAll()
                }
                is StreamContent -> {
                    response.status() ?: response.status(HttpStatusCode.OK)
                    ifNotHead {
                        val channel = response.channel()
                        value.stream(channel.asOutputStream())
                    }
                    close()
                    finishAll()
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

    private fun isNotHead() = request.httpMethod != HttpMethod.Head

    private inline fun ifNotHead(block: () -> Unit) {
        if (isNotHead()) {
            block()
        }
    }

    private fun PipelineContext<*>.sendAsyncChannel(channel: AsyncReadChannel): Nothing {
        if (isNotHead()) {
            val future = createMachineCompletableFuture()

            closeAtEnd(channel)
            channel.copyToAsyncThenComplete(response.channel(), future)
            pause()
        } else {
            close()
            finishAll()
        }
    }

    protected fun PipelineContext<*>.sendStream(stream: InputStream): Nothing {
        if (isNotHead()) {
            val future = createMachineCompletableFuture()

            closeAtEnd(stream)
            stream.asAsyncChannel().copyToAsyncThenComplete(response.channel(), future)
            pause()
        } else {
            close()
            finishAll()
        }
    }

    protected fun PipelineContext<*>.closeAtEnd(closeable: Closeable) {
        onSuccess {
            closeable.closeQuietly()
            close()
        }
        onFail {
            closeable.closeQuietly()
            close()
        }
    }

    private fun PipelineContext<*>.createMachineCompletableFuture() = CompletableFuture<Long>().apply {
        whenComplete { total, throwable ->
            try {
                if (throwable == null || throwable is PipelineContinue || throwable.cause is PipelineContinue) {
                    finishAll()
                } else if (throwable !is PipelineControlFlow && throwable.cause !is PipelineControlFlow) {
                    fail(throwable)
                }
            } catch (cont: PipelineContinue) {
                stateLoopMachine()
            }
        }
    }

    private fun PipelineContext<*>.stateLoopMachine() {
        do {
            try {
                proceed()
            } catch (e: PipelineContinue) {
            }
        } while (true);
    }

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (ignore: Throwable) {
        }
    }
}