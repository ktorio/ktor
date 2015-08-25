package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.cookies.*
import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.charset.*

public interface ApplicationResponse {
    public val headers: ResponseHeaders

    public fun status(): HttpStatusCode?
    public fun status(value: HttpStatusCode)
    public fun interceptStatus(handler: (value: HttpStatusCode, next: (value: HttpStatusCode) -> Unit) -> Unit)

    public fun cookie(name: String): Cookie? = header("Set-Cookie")?.let { listOf(parseServerSetCookieHeader(it)).firstOrNull { it.name == name } } // TODO multiheader
    public fun cookie(item: Cookie): Unit = header("Set-Cookie", renderSetCookieHeader(item))
    public fun interceptCookie(handler: (cookie: Cookie, next: (value: Cookie) -> Unit) -> Unit) {
        interceptHeader { name, value, next ->
            if (name == "Set-Cookie") {
                handler(parseServerSetCookieHeader(value)) { intercepted ->
                    next(name, renderSetCookieHeader(intercepted))
                }
            } else {
                next(name, value)
            }
        }
    }

    public fun stream(body: OutputStream.() -> Unit): Unit
    public fun interceptStream(handler: (body: OutputStream.() -> Unit, next: (body: OutputStream.() -> Unit) -> Unit) -> Unit)

    public fun send(message: Any): ApplicationRequestStatus
    public fun interceptSend(handler: (message: Any, next: (message: Any) -> ApplicationRequestStatus) -> ApplicationRequestStatus)
}

public abstract class ResponseHeaders {
    private val headersChain = Interceptable2<String, String, Unit> { name, value ->
        hostAppendHeader(name, value)
    }

    public fun contains(name: String): Boolean = getHostHeaderValues(name).isNotEmpty()
    public fun get(name: String): String? = getHostHeaderValues(name).firstOrNull()
    public fun values(name: String): List<String> = getHostHeaderValues(name)
    public fun allValues(): ValuesMap = getHostHeaderNames().fold(ValuesMap.Builder()) { builder, headerName ->
        builder.appendAll(headerName, getHostHeaderValues(headerName))
        builder
    }.build()

    public fun append(name: String, value: String) {
        headersChain.call(name, value)
    }

    public final fun intercept(handler: (name: String, value: String, next: (name: String, value: String) -> Unit) -> Unit) {
        headersChain.intercept(handler)
    }

    protected abstract fun hostAppendHeader(name: String, value: String)
    protected abstract fun getHostHeaderNames(): List<String>
    protected abstract fun getHostHeaderValues(name: String): List<String>
}

public fun ApplicationResponse.streamBytes(bytes: ByteArray) {
    stream { write(bytes) }
}

public fun ApplicationResponse.sendBytes(bytes: ByteArray): ApplicationRequestStatus {
    status(HttpStatusCode.OK)
    streamBytes(bytes)
    return ApplicationRequestStatus.Handled
}

public fun ApplicationResponse.streamText(text: String, encoding: String = "UTF-8"): ApplicationRequestStatus {
    return sendBytes(text.toByteArray(Charset.forName(encoding)))
}

public fun ApplicationResponse.sendText(contentType: ContentType, text: String): ApplicationRequestStatus {
    contentType(contentType)
    val encoding = contentType.parameter("charset") ?: "UTF-8"
    return sendBytes(text.toByteArray(Charset.forName(encoding)))
}

public fun ApplicationResponse.sendText(text: String): ApplicationRequestStatus {
    return sendText(ContentType.Text.Plain.withParameter("charset", "UTF-8"), text)
}

public fun ApplicationResponse.write(body: Writer.() -> Unit) {
    stream {
        writer().use { writer -> writer.body() }
    }
}