package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import java.io.*
import java.nio.charset.*

/**
 * Represents server's request
 */
public interface ApplicationResponse {
    public val headers: ResponseHeaders
    public val cookies: ResponseCookies

    public fun status(): HttpStatusCode?
    public fun status(value: HttpStatusCode)
    public fun interceptStatus(handler: (value: HttpStatusCode, next: (value: HttpStatusCode) -> Unit) -> Unit)

    public fun stream(body: OutputStream.() -> Unit): Unit
    public fun interceptStream(handler: (body: OutputStream.() -> Unit, next: (body: OutputStream.() -> Unit) -> Unit) -> Unit)

    public fun send(message: Any): ApplicationCallResult
    public fun interceptSend(handler: (message: Any, next: (message: Any) -> ApplicationCallResult) -> ApplicationCallResult)
}

public fun ApplicationResponse.streamBytes(bytes: ByteArray) {
    stream { write(bytes) }
}

public fun ApplicationResponse.streamText(text: String, encoding: String = "UTF-8") {
    streamBytes(text.toByteArray(Charset.forName(encoding)))
}

public fun ApplicationResponse.write(body: Writer.() -> Unit) {
    stream {
        writer().use { writer ->
            writer.body()
        }
    }
}