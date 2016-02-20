package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*

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
        if (value is HasETag) {
            response.etag(value.etag())
        }
        if (value is HasLastModified) {
            response.lastModified(value.lastModified)
        }
        if (value is HasContentType && !request.headers.contains(HttpHeaders.Range)) {
            response.contentType(value.contentType)
        }
        if (value is HasContentLength && !request.headers.contains(HttpHeaders.Range)) {
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
                    response.stream {
                        value.stream(this)
                    }
                    close()
                }
                is URIFileContent -> {
                    if (value.uri.scheme == "file") {
                        respond(LocalFileContent(File(value.uri)))
                    } else {
                        sendStream(value.stream())
                        close()
                    }
                }
                is ChannelContentProvider -> {
                    sendAsyncChannel(value.channel())
                    ApplicationCallResult.Handled // or async?
                }
                is LocalFileContent -> {
                    respond(object : ChannelContentProvider, HasVersions, HasContentLength {
                        override fun channel() = value.file.asyncReadOnlyFileChannel()
                        override val versions = listOf(LastModifiedVersion(Files.getLastModifiedTime(value.file.toPath())))
                        override val contentLength = value.file.length()
                        override val seekable = true
                    })
                }
                is StreamContentProvider -> {
                    sendStream(value.stream())
                    close()
                }
            }
        }
    }


    protected open fun sendAsyncChannel(channel: AsynchronousByteChannel) {
        response.stream {
            Channels.newInputStream(channel).use { it.copyTo(this) }
        }
        close()
    }

    protected open fun sendFile(file: File, position: Long, length: Long) {
        response.stream {
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { fc ->
                fc.transferTo(position, length, Channels.newChannel(this))
            }
        }
        close()
    }

    protected open fun sendStream(stream: InputStream) {
        response.stream {
            stream.use { it.copyTo(this) }
        }
        close()
    }
}