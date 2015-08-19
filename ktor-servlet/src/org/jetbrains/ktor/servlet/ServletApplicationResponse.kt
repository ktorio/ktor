package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import java.io.*
import javax.servlet.http.*

public class ServletApplicationResponse(private val servletResponse: HttpServletResponse) : ApplicationResponse {
    override val header = Interceptable2<String, String, ApplicationResponse> { name, value ->
        servletResponse.setHeader(name, value)
        this
    }

    override val status = Interceptable1<Int, ApplicationResponse> { code ->
        servletResponse.status = code
        this
    }

    override val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
        throw UnsupportedOperationException("No known way to stream value $value")
    }

    override val stream = Interceptable1<OutputStream.() -> Unit, ApplicationRequestStatus> { body ->
        servletResponse.outputStream.body()
        ApplicationRequestStatus.Handled
    }

}