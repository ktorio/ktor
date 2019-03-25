package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import io.ktor.client.request.*
import org.eclipse.jetty.http2.client.*
import java.util.concurrent.atomic.*

internal class JettyHttp2Engine(
    override val config: JettyEngineConfig
) : HttpClientJvmEngine("ktor-jetty") {
    private val jettyClient = HTTP2Client().apply {
        addBean(config.sslContextFactory)
        start()
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = createCallContext()
        return data.executeRequest(jettyClient, config, callContext)
    }

    override fun close() {
        super.close()
        jettyClient.stop()
    }
}
