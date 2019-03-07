package io.ktor.client.features.websocket

import io.ktor.client.*
import io.ktor.client.call.*

internal class WebSocketCall(client: HttpClient) : HttpClientCall(client)
