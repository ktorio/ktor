package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.response.*


suspend fun HttpResponse.receiveText(): String = receive()
