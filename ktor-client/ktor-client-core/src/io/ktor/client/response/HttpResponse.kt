package io.ktor.client.response

import io.ktor.client.call.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import java.io.*
import java.util.*


interface HttpResponse : HttpMessage, Closeable {

    val call: HttpClientCall

    val status: HttpStatusCode

    val version: HttpProtocolVersion

    val requestTime: Date

    val responseTime: Date

    val executionContext: Job

    fun receiveContent(): IncomingContent
}
