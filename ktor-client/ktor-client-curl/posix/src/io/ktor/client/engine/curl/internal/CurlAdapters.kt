package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.cinterop.*
import libcurl.*

// These should have been CPointer<CURL> and CPointer<CURLM>, I suppose,
// but somehow cinterop tool makes them just opaque pointers.
internal typealias EasyHandle = COpaquePointer

internal typealias MultiHandle = COpaquePointer

internal fun CURLMcode.verify() {
    if (this != CURLM_OK) {
        throw CurlIllegalStateException("Unexpected curl verify: ${curl_multi_strerror(this)?.toKString()}")
    }
}

internal fun CURLcode.verify() {
    if (this != CURLE_OK) {
        throw CurlIllegalStateException("Unexpected curl verify: ${curl_easy_strerror(this)?.toKString()}")
    }
}

internal fun EasyHandle.option(option: CURLoption, vararg variadicArguments: Any) {
    curl_easy_setopt(this, option, *variadicArguments).verify()
}

internal fun EasyHandle.getInfo(info: CURLINFO, vararg variadicArguments: Any) {
    curl_easy_getinfo(this, info, *variadicArguments).verify()
}

internal fun HttpRequest.headersToCurl(): CPointer<curl_slist> {
    var result: CPointer<curl_slist>? = null

    mergeHeaders(headers, content) { key, value ->
        val header = "$key: $value"
        result = curl_slist_append(result, header)
    }

    result = curl_slist_append(result, "Expect:")
    return result!!
}

internal fun UInt.fromCurl(): HttpProtocolVersion = when (this) {
    CURL_HTTP_VERSION_1_0 -> HttpProtocolVersion.HTTP_1_0
    CURL_HTTP_VERSION_1_1 -> HttpProtocolVersion.HTTP_1_1
    CURL_HTTP_VERSION_2_0 -> HttpProtocolVersion.HTTP_2_0
    CURL_HTTP_VERSION_2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
    else -> throw CurlIllegalStateException("Unsupported protocol: $this")
}
