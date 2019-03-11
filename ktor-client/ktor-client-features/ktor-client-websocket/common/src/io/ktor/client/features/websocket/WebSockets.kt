package io.ktor.client.features.websocket


import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Client WebSocket feature.
 *
 * @property pingInterval - interval between [FrameType.PING] messages.
 * @property maxFrameSize - max size of single websocket frame.
 */
@KtorExperimentalAPI
@UseExperimental(WebSocketInternalAPI::class)
class WebSockets(
    val pingInterval: Long = -1L,
    val maxFrameSize: Long = Int.MAX_VALUE.toLong()
) {
    internal val engine: WebSocketEngine by lazy { findWebSocketEngine() }

    private suspend fun execute(client: HttpClient, content: HttpRequestData): WebSocketCall {
        val clientEngine = client.engine
        val currentEngine = if (clientEngine is WebSocketEngine) clientEngine else engine

        val result = WebSocketCall(client)
        val request = DefaultHttpRequest(result, content)

        val response = currentEngine.execute(request).apply {
            call = result
        }

        result.response = response
        return result
    }

    @Suppress("KDocMissingDocumentation")
    companion object Feature : HttpClientFeature<Unit, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("Websocket")

        override fun prepare(block: Unit.() -> Unit): WebSockets = WebSockets()

        override fun install(feature: WebSockets, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { _ ->
                if (!context.url.protocol.isWebsocket()) return@intercept

                proceedWith(WebSocketContent())
            }

            val WebSocket = PipelinePhase("WebSocket")
            scope.sendPipeline.insertPhaseBefore(HttpSendPipeline.Engine, WebSocket)
            scope.sendPipeline.intercept(WebSocket) { content ->
                if (content !is WebSocketContent) return@intercept
                finish()

                context.body = content
                val requestData = context.build()

                proceedWith(feature.execute(scope, requestData))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, _) ->
                val response = context.response as? WebSocketResponse ?: return@intercept

                with(feature) {
                    val session = response.session
                    val expected = info.type

                    if (expected == DefaultClientWebSocketSession::class) {
                        val clientSession = DefaultClientWebSocketSession(context, session.asDefault())
                        proceedWith(HttpResponseContainer(info, clientSession))
                        return@intercept
                    }

                    proceedWith(HttpResponseContainer(info, DelegatingClientWebSocketSession(context, session)))
                }
            }
        }
    }

    private fun WebSocketSession.asDefault(): DefaultWebSocketSession {
        if (this is DefaultWebSocketSession) return this
        return DefaultWebSocketSession(this, pingInterval, maxFrameSize)
    }
}

@Suppress("KDocMissingDocumentation")
class WebSocketException(message: String) : IllegalStateException(message)
