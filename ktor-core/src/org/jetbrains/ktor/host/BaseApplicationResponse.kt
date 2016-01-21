package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*

abstract class BaseApplicationResponse : ApplicationResponse {
    protected abstract val stream: Interceptable1<OutputStream.() -> Unit, Unit>
    protected abstract val status: Interceptable1<HttpStatusCode, Unit>

    protected open val send = Interceptable1<Any, ApplicationCallResult> { value ->
        sendHeaders(value)
        when (value) {
            is String -> {
                status() ?: status(HttpStatusCode.OK)
                val encoding = headers[HttpHeaders.ContentType]?.let {
                    ContentType.parse(it).parameter("charset")
                } ?: "UTF-8"
                streamText(value, encoding)
                ApplicationCallResult.Handled
            }
            is TextContent -> {
                contentType(value.contentType)
                send(value.text)
            }
            is TextErrorContent -> {
                status(value.code)
                send(TextContent(ContentType.Text.Html, "<H1>${value.code}</H1>${value.message}"))
            }
            is HttpStatusCode -> {
                status(value)
                ApplicationCallResult.Handled
            }
            is StreamContent -> {
                stream {
                    value.stream(this)
                }
                ApplicationCallResult.Handled
            }
            is LocalFileContent -> {
                sendFile(value.file, 0L, value.file.length())
                ApplicationCallResult.Handled
            }
            is StreamContentProvider -> {
                sendStream(value.stream())
                ApplicationCallResult.Handled
            }
            else -> throw UnsupportedOperationException("No known way to stream value $value")
        }
    }

    protected fun sendHeaders(value: Any) {
        if (value is HasETag) {
            etag(value.etag())
        }
        if (value is HasLastModified) {
            lastModified(value.lastModified)
        }
        if (value is HasContentType) {
            contentType(value.contentType)
        }
        if (value is HasContentLength) {
            contentLength(value.contentLength) // TODO revisit it for partial request case
        }
    }

    protected open fun sendFile(file: File, position: Long, length: Long) {
        stream {
            file.inputStream().use { it.copyTo(this) }
        }
    }

    protected open fun sendStream(stream: InputStream) {
        stream {
            stream.copyTo(this)
        }
    }

    override fun send(message: Any): ApplicationCallResult = send.execute(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationCallResult) -> ApplicationCallResult) = send.intercept(handler)

    override val cookies = ResponseCookies(this)

    override fun status(value: HttpStatusCode) = status.execute(value)
    override fun interceptStatus(handler: (HttpStatusCode, (HttpStatusCode) -> Unit) -> Unit) = status.intercept(handler)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.execute(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)
}