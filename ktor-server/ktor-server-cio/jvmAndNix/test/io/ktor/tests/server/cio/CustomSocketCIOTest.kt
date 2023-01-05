package io.ktor.tests.server.cio

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.*
import io.ktor.server.cio.internal.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.file.*
import kotlin.io.path.*
import kotlin.test.*

class CustomSocketCIOTest {
    @Test
    fun connectServerAndClientOverUnixSocket() = testSuspend {
        val socket = Files.createTempFile("network", "socket").absolutePathString()

        val client = HttpClient(CIOSocketClient(socket))
        val server = embeddedServer(CIOSocket(socket)) {
            routing {
                get("/hello") {
                    call.respondText("Get from Socket")
                }
                post("/hello") {
                    call.respondText("Post from Socket")
                }
            }
        }.start(wait = false)
        assertEquals("Get from Socket", client.get("/hello").bodyAsText())
        assertEquals("Post from Socket", client.post("/hello").bodyAsText())
        server.stop(1000L, 1000L)
    }
}

class CIOSocketClient(private val socket: String) : HttpClientEngineFactory<CIOEngineConfig> {
    override fun create(block: CIOEngineConfig.() -> Unit): CIOSocketClientEngine {
        return CIOSocketClientEngine(socket, CIOEngineConfig().apply(block))
    }
}

@OptIn(InternalAPI::class)
class CIOSocketClientEngine(private val socket: String, config: CIOEngineConfig) :
    CIOHttpClientEngine by CIOEngine(config) {
    override fun createAddress(host: String, port: Int): SocketAddress = UnixSocketAddress(socket)
}

class CIOSocket(private val socket: String) :
    ApplicationEngineFactory<CIOSocketApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: CIOApplicationEngine.Configuration.() -> Unit
    ): CIOSocketApplicationEngine = CIOSocketApplicationEngine(socket, environment, configure)
}

class CIOSocketApplicationEngine(
    private val socket: String,
    environment: ApplicationEngineEnvironment,
    configure: CIOApplicationEngine.Configuration.() -> Unit
) : CIOApplicationEngineInterface by CIOApplicationEngine(environment, configure) {

    @InternalAPI
    override fun CoroutineScope.startHttpServer(
        connectorConfig: EngineConnectorConfig,
        connectionIdleTimeoutSeconds: Long,
        handleRequest: suspend ServerRequestScope.(Request) -> Unit
    ): HttpServer {
        val selector = SelectorManager(coroutineContext)
        return httpServer(
            createServer = {
                aSocket(selector).tcp().bind(UnixSocketAddress(socket))
            },
            timeout = WeakTimeoutQueue(connectionIdleTimeoutSeconds * 1000L),
            serverJobName = CoroutineName("server-root-$socket"),
            acceptJobName = CoroutineName("accept-$socket"),
            selector = selector
        ) { request ->
            handleRequest(request)
        }
    }
}
