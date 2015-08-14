package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.charset.*

public interface ApplicationResponse {
    public val header: Interceptable2<String, String, ApplicationResponse>
    public val status: Interceptable1<Int, ApplicationResponse>

    public fun stream(body: OutputStream.() -> Unit): ApplicationRequestStatus
}

public fun ApplicationResponse.write(body: Writer.() -> Unit): ApplicationRequestStatus = stream {
    writer().use { writer ->
        writer.body()
    }
}

public fun ApplicationResponse.send<T>(message: T, messageWriter: OutputStream.(T) -> Unit): ApplicationRequestStatus {
    return stream {
        messageWriter(message)
    }
}

public fun ApplicationResponse.sendText(text: String, encoding: String = "UTF-8"): ApplicationRequestStatus = send(text to encoding) { pair ->
    val (text, encoding) = pair
    write(text.toByteArray(Charset.forName(encoding)))
}

public fun ApplicationResponse.sendBytes(bytes: ByteArray): ApplicationRequestStatus = send(bytes) { bytes ->
    write(bytes)
}

public inline fun ApplicationResponse.header(name: String, value: String): ApplicationResponse = header.call(name, value)
public inline fun ApplicationResponse.status(code: Int): ApplicationResponse = status.call(code)
