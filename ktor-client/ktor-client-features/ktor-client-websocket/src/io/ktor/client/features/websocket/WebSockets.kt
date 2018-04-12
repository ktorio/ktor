package io.ktor.client.features.websocket

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*

class WebSockets(
    val maxFrameSize: Long = Int.MAX_VALUE.toLong()
) : AutoCloseable {
    val context = CompletableDeferred<Unit>()

    override fun close() {
        context.complete(Unit)
    }

    companion object Feature : HttpClientFeature<Unit, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("Websocket")

        override suspend fun prepare(block: Unit.() -> Unit): WebSockets = WebSockets()

        override fun install(feature: WebSockets, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { _ ->
                if (!context.url.protocol.isWebsocket()) return@intercept
                proceedWith(WebSocketContent())
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (type, response) ->
                val content = context.request.content

                if (type != WebSocketSession::class
                    || response !is HttpResponse
                    || response.status != HttpStatusCode.SwitchingProtocols
                    || content !is WebSocketContent
                ) return@intercept

                content.verify(response.headers)

                val session = RawWebSocket(
                    response.content, content.output,
                    feature.maxFrameSize,
                    dispatcher = scope.dispatcher
                )

                proceedWith(HttpResponseContainer(type, session))
            }
        }
    }
}
