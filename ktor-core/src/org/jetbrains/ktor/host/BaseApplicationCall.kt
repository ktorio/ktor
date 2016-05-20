package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.charset.*
import java.util.concurrent.*

abstract class BaseApplicationCall(override val application: Application, override val executor: Executor) : ApplicationCall {
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

    protected fun commit(o: FinalContent) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, value) in o.headers.flattenEntries()) {
            response.header(name, value)
        }
    }

    private val respond = Pipeline<Any>()

    init {
        respond.intercept { value ->
            when (value) {
                is String -> {
                    val encoding = response.headers[HttpHeaders.ContentType]?.let {
                        ContentType.parse(it).parameter("charset")
                    } ?: "UTF-8"

                    respond(TextContentResponse(null, null, encoding, value))
                }
                is TextContent -> {
                    respond(TextContentResponse(null, value.contentType,
                            value.contentType.parameter("charset") ?: "UTF-8",
                            value.text))
                }
                is HttpStatusContent -> {
                    respond(TextContentResponse(value.code,
                            ContentType.Text.Html.withParameter("charset", "UTF-8"), "UTF-8",
                            "<H1>${value.code}</H1>${value.message}"))
                }
                is HttpStatusCode -> {
                    response.status(value)
                    close()
                    finishAll()
                }
                is FinalContent.StreamConsumer -> {
                    val pipe = AsyncPipe()
                    closeAtEnd(pipe)

                    // note: it is very important to resend it here rather than just use value.startContent
                    respond(PipeResponse(pipe, { value.headers }) {
                        executor.execute {
                            try {
                                value.stream(pipe.asOutputStream())
                            } finally {
                                pipe.close()
                            }
                        }
                    })
                }
                is URIFileContent -> { // TODO it should be better place for that purpose
                    if (value.uri.scheme == "file") {
                        respond(LocalFileContent(File(value.uri)))
                    } else {
                        commit(value)
                        value.startContent(this@BaseApplicationCall, this)
                    }
                }
                is FinalContent -> {
                    commit(value)
                    value.startContent(this@BaseApplicationCall, this)
                }
            }
        }
    }

    private class PipeResponse(val pipe: AsyncPipe, val headersDelegate: () -> ValuesMap, val start: () -> Unit) : FinalContent.ChannelContent() {
        override val headers: ValuesMap
            get() = headersDelegate()

        override fun channel(): AsyncReadChannel {
            start()
            return pipe
        }
    }

    private class TextContentResponse(override val status: HttpStatusCode?, val contentType: ContentType?, val encoding: String, val text: String) : FinalContent.ChannelContent() {
        private val bytes by lazy { text.toByteArray(Charset.forName(encoding)) }

        override val headers: ValuesMap
            get() = ValuesMap.build(true) {
                if (contentType != null) {
                    contentType(contentType)
                }
                contentLength(bytes.size.toLong())
            }

        override fun channel() = ByteArrayAsyncReadChannel(bytes)
    }
}