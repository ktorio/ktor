package org.jetbrains.ktor.client

import org.eclipse.jetty.http2.server.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import org.eclipse.jetty.util.thread.*
import org.junit.*
import java.net.*
import javax.servlet.http.*
import kotlin.test.*


class JettyHttp2ClientTest {

    private var server: Server? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun smokeTest() {
        server = createServer("/", 9096) { request, response ->
            response.status = HttpServletResponse.SC_OK
            response.contentType = "text/plain;charset=utf-8"
            response.writer.apply {
                for (i in 1..10) {
                    append("try$i")
                    flush()
                }
            }
        }

        val r = JettyHttp2Client.openBlocking(URL("http://localhost:9096/"))
        try {
            assertEquals(200, r.status.value)

            assertEquals("try1try2try3try4try5try6try7try8try9try10", r.stream.reader().readText())
        } finally {
            r.connection.close()
        }
    }

    companion object {
        val serverExecutor = QueuedThreadPool().apply {
            name = "server"
            start()
        }

        @JvmStatic
        @AfterClass
        fun shutdown() {
            serverExecutor.stop()
        }

        fun createServer(path: String, port: Int, handler: (HttpServletRequest, HttpServletResponse) -> Unit): Server {
            val server = Server(serverExecutor)
            val connector = ServerConnector(server, 1, 1, HTTP2ServerConnectionFactory(HttpConfiguration()))
            connector.port = port
            server.addConnector(connector)

            val context = ServletContextHandler(server, "/", true, false)
            context.addServlet(ServletHolder(object : DefaultServlet() {
                override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                    handler(request, response)
                }
            }), path)

            server.start()

            return server
        }
    }

}