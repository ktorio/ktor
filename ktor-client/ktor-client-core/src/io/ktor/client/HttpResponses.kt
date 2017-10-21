package io.ktor.client

import io.ktor.client.call.*


suspend fun HttpClientCall.receiveText(): String = receive<String>()
