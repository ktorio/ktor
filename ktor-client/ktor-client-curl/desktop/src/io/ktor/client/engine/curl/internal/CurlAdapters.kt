/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import libcurl.*

// These should have been CPointer<CURL> and CPointer<CURLM>, I suppose,
// but somehow cinterop tool makes them just opaque pointers.
@OptIn(ExperimentalForeignApi::class)
internal typealias EasyHandle = COpaquePointer

@OptIn(ExperimentalForeignApi::class)
internal typealias MultiHandle = COpaquePointer

/**
 * Curl manages websocket headers internally:
 * @see <a href="https://github.com/curl/curl/blob/f0986c6e18417865f49e725201a5224d9b5af849/lib/ws.c#L684">List of headers</a>
 */
internal val DISALLOWED_WEBSOCKET_HEADERS = setOf(
    HttpHeaders.Upgrade,
    HttpHeaders.Connection,
    HttpHeaders.SecWebSocketVersion,
    HttpHeaders.SecWebSocketKey
)

@OptIn(ExperimentalForeignApi::class)
internal fun CURLMcode.verify() {
    if (this != CURLM_OK) {
        error("Unexpected curl verify: ${curl_multi_strerror(this)?.toKString()} (${curlMCodeName(this)})")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun curlMCodeName(code: CURLMcode): String = when (code) {
    CURLM_CALL_MULTI_PERFORM -> "CURLM_CALL_MULTI_PERFORM"
    CURLM_OK -> "CURLM_OK"
    CURLM_BAD_HANDLE -> "CURLM_BAD_HANDLE"
    CURLM_BAD_EASY_HANDLE -> "CURLM_BAD_EASY_HANDLE"
    CURLM_OUT_OF_MEMORY -> "CURLM_OUT_OF_MEMORY"
    CURLM_INTERNAL_ERROR -> "CURLM_INTERNAL_ERROR"
    CURLM_BAD_SOCKET -> "CURLM_BAD_SOCKET"
    CURLM_UNKNOWN_OPTION -> "CURLM_UNKNOWN_OPTION"
    CURLM_ADDED_ALREADY -> "CURLM_ADDED_ALREADY"
    CURLM_RECURSIVE_API_CALL -> "CURLM_RECURSIVE_API_CALL"
    CURLM_WAKEUP_FAILURE -> "CURLM_WAKEUP_FAILURE"
    CURLM_BAD_FUNCTION_ARGUMENT -> "CURLM_BAD_FUNCTION_ARGUMENT"
    CURLM_ABORTED_BY_CALLBACK -> "CURLM_ABORTED_BY_CALLBACK"
    CURLM_UNRECOVERABLE_POLL -> "CURLM_UNRECOVERABLE_POLL"
    else -> code.toString()
}

@OptIn(ExperimentalForeignApi::class)
internal fun CURLcode.verify() {
    if (this != CURLE_OK) {
        error("Unexpected curl verify: ${curl_easy_strerror(this)?.toKString()} (${curlCodeName(this)})")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun curlCodeName(code: CURLcode): String = when (code) {
    CURLE_OK -> "CURLE_OK"
    CURLE_UNSUPPORTED_PROTOCOL -> "CURLE_UNSUPPORTED_PROTOCOL"
    CURLE_FAILED_INIT -> "CURLE_FAILED_INIT"
    CURLE_URL_MALFORMAT -> "CURLE_URL_MALFORMAT"
    CURLE_NOT_BUILT_IN -> "CURLE_NOT_BUILT_IN"
    CURLE_COULDNT_RESOLVE_PROXY -> "CURLE_COULDNT_RESOLVE_PROXY"
    CURLE_COULDNT_RESOLVE_HOST -> "CURLE_COULDNT_RESOLVE_HOST"
    CURLE_COULDNT_CONNECT -> "CURLE_COULDNT_CONNECT"
    CURLE_WEIRD_SERVER_REPLY -> "CURLE_WEIRD_SERVER_REPLY"
    CURLE_REMOTE_ACCESS_DENIED -> "CURLE_REMOTE_ACCESS_DENIED"
    CURLE_FTP_ACCEPT_FAILED -> "CURLE_FTP_ACCEPT_FAILED"
    CURLE_FTP_WEIRD_PASV_REPLY -> "CURLE_FTP_WEIRD_PASV_REPLY"
    CURLE_FTP_WEIRD_227_FORMAT -> "CURLE_FTP_WEIRD_227_FORMAT"
    CURLE_FTP_CANT_GET_HOST -> "CURLE_FTP_CANT_GET_HOST"
    CURLE_HTTP2 -> "CURLE_HTTP2"
    CURLE_FTP_COULDNT_SET_TYPE -> "CURLE_FTP_COULDNT_SET_TYPE"
    CURLE_PARTIAL_FILE -> "CURLE_PARTIAL_FILE"
    CURLE_FTP_COULDNT_RETR_FILE -> "CURLE_FTP_COULDNT_RETR_FILE"
    else -> code.toString()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: Int) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: Long) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: CPointer<*>) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: CValuesRef<*>) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: String) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.getInfo(info: CURLINFO, optionValue: CPointer<*>) {
    curl_easy_getinfo(this, info, optionValue).verify()
}

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal fun HttpRequestData.headersToCurl(): CPointer<curl_slist> {
    var result: CPointer<curl_slist>? = null

    val isUpgradeRequest = isUpgradeRequest()
    forEachHeader { key, value ->
        if (isUpgradeRequest && key in DISALLOWED_WEBSOCKET_HEADERS) return@forEachHeader
        val header = "$key: $value"
        result = curl_slist_append(result, header)
    }

    result = curl_slist_append(result, "Expect:")
    return result!!
}

@OptIn(ExperimentalForeignApi::class)
internal fun UInt.fromCurl(): HttpProtocolVersion = when (this) {
    CURL_HTTP_VERSION_1_0 -> HttpProtocolVersion.HTTP_1_0
    CURL_HTTP_VERSION_1_1 -> HttpProtocolVersion.HTTP_1_1
    CURL_HTTP_VERSION_2_0 -> HttpProtocolVersion.HTTP_2_0
    // old curl fallback
    else -> HttpProtocolVersion.HTTP_1_1
}
