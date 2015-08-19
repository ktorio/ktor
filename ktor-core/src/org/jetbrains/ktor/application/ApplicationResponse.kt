package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.charset.*

public interface ApplicationResponse {
    public val header: Interceptable2<String, String, ApplicationResponse>
    public val status: Interceptable1<Int, ApplicationResponse>

    public val send: Interceptable1<Any, ApplicationRequestStatus>
    public val stream: Interceptable1<OutputStream.() -> Unit, ApplicationRequestStatus>
}

public fun ApplicationResponse.stream(body: OutputStream.() -> Unit): ApplicationRequestStatus {
    return stream.call(body)
}

public fun ApplicationResponse.send(message: Any): ApplicationRequestStatus {
    return send.call(message)
}

public fun ApplicationResponse.sendBytes(bytes: ByteArray): ApplicationRequestStatus {
    return stream { write(bytes) }
}

public fun ApplicationResponse.sendText(text: String, encoding: String = "UTF-8"): ApplicationRequestStatus {
    return sendBytes(text.toByteArray(Charset.forName(encoding)))
}

public fun ApplicationResponse.write(body: Writer.() -> Unit): ApplicationRequestStatus = stream {
    writer().use { writer -> writer.body() }
}


public inline fun ApplicationResponse.header(name: String, value: String): ApplicationResponse = header.call(name, value)
public inline fun ApplicationResponse.status(code: Int): ApplicationResponse = status.call(code)
