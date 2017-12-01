package io.ktor.client.response

import io.ktor.client.call.*
import io.ktor.content.*
import io.ktor.http.*
import java.io.*
import java.util.*


interface BaseHttpResponse : HttpMessage, Closeable {

    val call: HttpClientCall

    val status: HttpStatusCode

    val version: HttpProtocolVersion

    val requestTime: Date

    val responseTime: Date

    fun receiveContent(): IncomingContent
}
