package io.ktor.client.engine.jetty

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import kotlinx.coroutines.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.util.ssl.*
import java.net.*


internal class JettyHttp2Engine(
    override val config: JettyEngineConfig
) : HttpClientJvmEngine("ktor-jetty") {

    private val sslContextFactory: SslContextFactory = config.sslContextFactory

    private val jettyClient = HTTP2Client().apply {
        addBean(sslContextFactory)
        start()
    }

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val callContext = createCallContext()
        val request = JettyHttpRequest(call, this, data, callContext)
        val response = request.execute()

        return HttpEngineCall(request, response)
    }

    internal suspend fun connect(host: String, port: Int): Session = withPromise { promise ->
        jettyClient.connect(sslContextFactory, InetSocketAddress(host, port), Session.Listener.Adapter(), promise)
    }

    override fun close() {
        super.close()

        jettyClient.stop()
    }
}
