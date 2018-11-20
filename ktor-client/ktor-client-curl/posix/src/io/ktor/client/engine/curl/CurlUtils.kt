package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.cinterop.*
import libcurl.*

// These should have been CPointer<CURL> and CPointer<CURLM>, I suppose,
// but somehow cinterop tool makes them just opaque pointers.
typealias EasyHandle = COpaquePointer
typealias MultiHandle = COpaquePointer

internal fun CURLMcode.code() {
    if (this != CURLM_OK) {
        throw CurlIllegalStateException("unexpected curl code: ${curl_multi_strerror(this)?.toKString()}")
    }
}

internal fun CURLcode.code() {
    if (this != CURLE_OK) {
        throw CurlIllegalStateException("unexpected curl code: ${curl_easy_strerror(this)?.toKString()}")
    }
}

internal fun EasyHandle.option(option: CURLoption, vararg variadicArguments: Any?) {
    curl_easy_setopt(this, option, *variadicArguments)
        .code()
}

internal fun EasyHandle.getinfo(info: CURLINFO, vararg variadicArguments: Any?) {
    curl_easy_getinfo(this, info, *variadicArguments)
        .code()
}

internal fun headersToCurl(request: HttpRequest): CPointer<curl_slist>? {
    var cList: CPointer<curl_slist>? = null

    mergeHeaders(request.headers, request.content) { key, value ->
        val header = "$key: $value"
        curl_slist_append(cList, header)
    }

    return cList
}

fun UInt.fromCurl(): HttpProtocolVersion = when (this) {
    CURL_HTTP_VERSION_1_0 -> HttpProtocolVersion.HTTP_1_0
    CURL_HTTP_VERSION_1_1 -> HttpProtocolVersion.HTTP_1_1
    CURL_HTTP_VERSION_2_0 -> HttpProtocolVersion.HTTP_2_0
    CURL_HTTP_VERSION_2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
    else -> throw CurlUnsupportedProtocolException(this)
}
