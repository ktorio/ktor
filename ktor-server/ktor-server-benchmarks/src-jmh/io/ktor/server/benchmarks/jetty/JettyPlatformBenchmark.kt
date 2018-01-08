package io.ktor.server.benchmarks.jetty

import io.ktor.server.benchmarks.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.util.*
import java.io.*
import javax.servlet.*
import javax.servlet.http.*

class JettyPlatformBenchmark : PlatformBenchmark() {
    lateinit var server: Server

    override fun runServer(port: Int) {
        server = Server(port)
        val connector = server.getBean(ServerConnector::class.java)
        val config = connector.getBean(HttpConnectionFactory::class.java).httpConfiguration
        config.sendDateHeader = false
        config.sendServerVersion = false

        val pathHandler = PathHandler()
        server.handler = pathHandler

        server.start()
    }

    override fun stopServer() {
        server.stop()
    }

    private class PathHandler : AbstractHandler() {
        var _plainHandler = PlainTextHandler()

        init {
            addBean(_plainHandler)
        }

        override fun setServer(server: Server) {
            super.setServer(server)
            _plainHandler.server = server
        }

        @Throws(IOException::class, ServletException::class)
        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            when (target) {
                "/sayOK" -> _plainHandler.handle(target, baseRequest, request, response)
            }
        }

    }

    private class PlainTextHandler : AbstractHandler() {
        internal var helloWorld = BufferUtil.toBuffer("OK")
        internal var contentType: HttpField = PreEncodedHttpField(HttpHeader.CONTENT_TYPE, MimeTypes.Type.TEXT_PLAIN.asString())

        @Throws(IOException::class, ServletException::class)
        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            baseRequest.isHandled = true
            baseRequest.response.httpFields.add(contentType)
            baseRequest.response.httpOutput.sendContent(helloWorld.slice())
        }
    }
}