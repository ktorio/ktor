package io.ktor.client.engine.curl

import io.ktor.client.engine.curl.*
import kotlinx.cinterop.*
import libcurl.*

// Thess classes are frozen to pass data between the curl driver and curl worker threads.

internal class CurlRequestData(
    val url: String,
    val method: String,
    val headers: CPointer<curl_slist>?,
    val content: ByteArray?
    // val attributes: Attributes
)

internal class CurlResponseData(
    val request: CurlRequestData,
    val chunks: MutableList<ByteArray> = mutableListOf(),
    val headers: MutableList<ByteArray> = mutableListOf()
) {
    /* lateinit */ var status: Int = 0
    /* lateinit */ var version: UInt = 0u
}

internal class CurlRequest(
    val newRequests: List<CurlRequestData>,
    override val listenerKey: ListenerKey
) : WorkerRequest

internal class CurlResponse(
    val completeResponses: List<CurlResponseData>,
    override val listenerKey: ListenerKey
) : WorkerResponse
