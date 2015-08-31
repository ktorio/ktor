package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import javax.servlet.http.*

public class ServletApplicationResponse(private val servletResponse: HttpServletResponse) : ApplicationResponse {
    private val header = Interceptable2<String, String, Unit> { name, value ->
        servletResponse.setHeader(name, value)
    }

    var _status: HttpStatusCode? = null
    private val status = Interceptable1<HttpStatusCode, Unit> { code ->
        _status = code
        servletResponse.status = code.value
    }

    public override fun header(name: String): String = servletResponse.getHeader(name)
    public override fun header(name: String, value: String) = header.call(name, value)
    public override fun interceptHeader(handler: (String, String, (String, String) -> Unit) -> Unit) = header.intercept(handler)

    public override fun status(): HttpStatusCode? = _status
    public override fun status(value: HttpStatusCode) = status.call(value)
    public override fun interceptStatus(handler: (HttpStatusCode, (HttpStatusCode) -> Unit) -> Unit) = status.intercept(handler)

    private val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
        throw UnsupportedOperationException("No known way to stream value $value")
    }

    private val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        servletResponse.outputStream.body()
        ApplicationRequestStatus.Handled
    }

    override fun send(message: Any): ApplicationRequestStatus = send.call(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationRequestStatus) -> ApplicationRequestStatus) = send.intercept(handler)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.call(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)

}