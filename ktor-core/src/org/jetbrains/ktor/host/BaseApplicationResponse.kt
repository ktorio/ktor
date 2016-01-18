package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*

public abstract class BaseApplicationResponse : ApplicationResponse {
    protected abstract val stream: Interceptable1<OutputStream.() -> Unit, Unit>
    protected abstract val status: Interceptable1<HttpStatusCode, Unit>

    protected open val send = Interceptable1<Any, ApplicationCallResult> { value ->
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
            is HasContent -> {
                stream {
                    value.stream(this)
                }
                ApplicationCallResult.Handled
            }
            else -> throw UnsupportedOperationException("No known way to stream value $value")
        }
    }

    init {
        interceptSend { value, next ->
            if (value is HasETag) {
                header(HttpHeaders.ETag, value.etag())
            }
            if (value is HasLastModified) {
                header(HttpHeaders.LastModified, value.lastModified)
            }
            if (value is HasContentType) {
                contentType(value.contentType)
            }
            if (value is HasContentLength) {
                header(HttpHeaders.ContentLength, value.contentLength) // TODO revisit it for partial request case
            }

            next(value)
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