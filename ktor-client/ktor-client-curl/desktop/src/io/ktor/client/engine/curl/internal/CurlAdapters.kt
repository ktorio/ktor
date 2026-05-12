/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalForeignApi::class)

package io.ktor.client.engine.curl.internal

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import libcurl.*

// These should have been CPointer<CURL> and CPointer<CURLM>, I suppose,
// but somehow cinterop tool makes them just opaque pointers.
internal typealias EasyHandle = COpaquePointer
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

internal fun CURLMcode.verify() {
    check(this == CURLM_OK) { "Unexpected curl verify: $errorMessage" }
}

private val CURLMcode.errorMessage: String
    get() = "${curl_multi_strerror(this)?.toKString()} ($name)"

private val CURLMcode.name: String
    get() = when (this) {
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
        else -> toString()
    }

internal fun CURLcode.verify() {
    check(this == CURLE_OK) { "Unexpected curl verify: $errorMessage" }
}

internal val CURLcode.errorMessage: String
    get() = "${curl_easy_strerror(this)?.toKString()} ($name)"

private val CURLcode.name: String
    get() = when (this) {
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
        CURLE_FTP_WEIRD_PASS_REPLY -> "CURLE_FTP_WEIRD_PASS_REPLY"
        CURLE_FTP_ACCEPT_TIMEOUT -> "CURLE_FTP_ACCEPT_TIMEOUT"
        CURLE_FTP_WEIRD_PASV_REPLY -> "CURLE_FTP_WEIRD_PASV_REPLY"
        CURLE_FTP_WEIRD_227_FORMAT -> "CURLE_FTP_WEIRD_227_FORMAT"
        CURLE_FTP_CANT_GET_HOST -> "CURLE_FTP_CANT_GET_HOST"
        CURLE_HTTP2 -> "CURLE_HTTP2"
        CURLE_FTP_COULDNT_SET_TYPE -> "CURLE_FTP_COULDNT_SET_TYPE"
        CURLE_PARTIAL_FILE -> "CURLE_PARTIAL_FILE"
        CURLE_FTP_COULDNT_RETR_FILE -> "CURLE_FTP_COULDNT_RETR_FILE"
        CURLE_QUOTE_ERROR -> "CURLE_QUOTE_ERROR"
        CURLE_HTTP_RETURNED_ERROR -> "CURLE_HTTP_RETURNED_ERROR"
        CURLE_WRITE_ERROR -> "CURLE_WRITE_ERROR"
        CURLE_UPLOAD_FAILED -> "CURLE_UPLOAD_FAILED"
        CURLE_READ_ERROR -> "CURLE_READ_ERROR"
        CURLE_OUT_OF_MEMORY -> "CURLE_OUT_OF_MEMORY"
        CURLE_OPERATION_TIMEDOUT -> "CURLE_OPERATION_TIMEDOUT"
        CURLE_FTP_PORT_FAILED -> "CURLE_FTP_PORT_FAILED"
        CURLE_FTP_COULDNT_USE_REST -> "CURLE_FTP_COULDNT_USE_REST"
        CURLE_RANGE_ERROR -> "CURLE_RANGE_ERROR"
        CURLE_SSL_CONNECT_ERROR -> "CURLE_SSL_CONNECT_ERROR"
        CURLE_BAD_DOWNLOAD_RESUME -> "CURLE_BAD_DOWNLOAD_RESUME"
        CURLE_FILE_COULDNT_READ_FILE -> "CURLE_FILE_COULDNT_READ_FILE"
        CURLE_LDAP_CANNOT_BIND -> "CURLE_LDAP_CANNOT_BIND"
        CURLE_LDAP_SEARCH_FAILED -> "CURLE_LDAP_SEARCH_FAILED"
        CURLE_ABORTED_BY_CALLBACK -> "CURLE_ABORTED_BY_CALLBACK"
        CURLE_BAD_FUNCTION_ARGUMENT -> "CURLE_BAD_FUNCTION_ARGUMENT"
        CURLE_INTERFACE_FAILED -> "CURLE_INTERFACE_FAILED"
        CURLE_TOO_MANY_REDIRECTS -> "CURLE_TOO_MANY_REDIRECTS"
        CURLE_UNKNOWN_OPTION -> "CURLE_UNKNOWN_OPTION"
        CURLE_SETOPT_OPTION_SYNTAX -> "CURLE_SETOPT_OPTION_SYNTAX"
        CURLE_GOT_NOTHING -> "CURLE_GOT_NOTHING"
        CURLE_SSL_ENGINE_NOTFOUND -> "CURLE_SSL_ENGINE_NOTFOUND"
        CURLE_SSL_ENGINE_SETFAILED -> "CURLE_SSL_ENGINE_SETFAILED"
        CURLE_SEND_ERROR -> "CURLE_SEND_ERROR"
        CURLE_RECV_ERROR -> "CURLE_RECV_ERROR"
        CURLE_SSL_CERTPROBLEM -> "CURLE_SSL_CERTPROBLEM"
        CURLE_SSL_CIPHER -> "CURLE_SSL_CIPHER"
        CURLE_PEER_FAILED_VERIFICATION -> "CURLE_PEER_FAILED_VERIFICATION"
        CURLE_BAD_CONTENT_ENCODING -> "CURLE_BAD_CONTENT_ENCODING"
        CURLE_FILESIZE_EXCEEDED -> "CURLE_FILESIZE_EXCEEDED"
        CURLE_USE_SSL_FAILED -> "CURLE_USE_SSL_FAILED"
        CURLE_SEND_FAIL_REWIND -> "CURLE_SEND_FAIL_REWIND"
        CURLE_SSL_ENGINE_INITFAILED -> "CURLE_SSL_ENGINE_INITFAILED"
        CURLE_LOGIN_DENIED -> "CURLE_LOGIN_DENIED"
        CURLE_TFTP_NOTFOUND -> "CURLE_TFTP_NOTFOUND"
        CURLE_TFTP_PERM -> "CURLE_TFTP_PERM"
        CURLE_REMOTE_DISK_FULL -> "CURLE_REMOTE_DISK_FULL"
        CURLE_TFTP_ILLEGAL -> "CURLE_TFTP_ILLEGAL"
        CURLE_TFTP_UNKNOWNID -> "CURLE_TFTP_UNKNOWNID"
        CURLE_REMOTE_FILE_EXISTS -> "CURLE_REMOTE_FILE_EXISTS"
        CURLE_TFTP_NOSUCHUSER -> "CURLE_TFTP_NOSUCHUSER"
        CURLE_SSL_CACERT_BADFILE -> "CURLE_SSL_CACERT_BADFILE"
        CURLE_REMOTE_FILE_NOT_FOUND -> "CURLE_REMOTE_FILE_NOT_FOUND"
        CURLE_SSH -> "CURLE_SSH"
        CURLE_SSL_SHUTDOWN_FAILED -> "CURLE_SSL_SHUTDOWN_FAILED"
        CURLE_AGAIN -> "CURLE_AGAIN"
        CURLE_SSL_CRL_BADFILE -> "CURLE_SSL_CRL_BADFILE"
        CURLE_SSL_ISSUER_ERROR -> "CURLE_SSL_ISSUER_ERROR"
        CURLE_FTP_PRET_FAILED -> "CURLE_FTP_PRET_FAILED"
        CURLE_RTSP_CSEQ_ERROR -> "CURLE_RTSP_CSEQ_ERROR"
        CURLE_RTSP_SESSION_ERROR -> "CURLE_RTSP_SESSION_ERROR"
        CURLE_FTP_BAD_FILE_LIST -> "CURLE_FTP_BAD_FILE_LIST"
        CURLE_CHUNK_FAILED -> "CURLE_CHUNK_FAILED"
        CURLE_NO_CONNECTION_AVAILABLE -> "CURLE_NO_CONNECTION_AVAILABLE"
        CURLE_SSL_PINNEDPUBKEYNOTMATCH -> "CURLE_SSL_PINNEDPUBKEYNOTMATCH"
        CURLE_SSL_INVALIDCERTSTATUS -> "CURLE_SSL_INVALIDCERTSTATUS"
        CURLE_HTTP2_STREAM -> "CURLE_HTTP2_STREAM"
        CURLE_RECURSIVE_API_CALL -> "CURLE_RECURSIVE_API_CALL"
        CURLE_AUTH_ERROR -> "CURLE_AUTH_ERROR"
        CURLE_HTTP3 -> "CURLE_HTTP3"
        CURLE_QUIC_CONNECT_ERROR -> "CURLE_QUIC_CONNECT_ERROR"
        CURLE_PROXY -> "CURLE_PROXY"
        CURLE_SSL_CLIENTCERT -> "CURLE_SSL_CLIENTCERT"
        CURLE_UNRECOVERABLE_POLL -> "CURLE_UNRECOVERABLE_POLL"
        CURLE_TOO_LARGE -> "CURLE_TOO_LARGE"
        CURLE_ECH_REQUIRED -> "CURLE_ECH_REQUIRED"
        else -> toString()
    }

internal fun EasyHandle.option(option: CURLoption, optionValue: Int) {
    curl_easy_setopt(this, option, optionValue).verify()
}

internal fun EasyHandle.option(option: CURLoption, optionValue: Long) {
    curl_easy_setopt(this, option, optionValue).verify()
}

internal fun EasyHandle.option(option: CURLoption, optionValue: CPointer<*>) {
    curl_easy_setopt(this, option, optionValue).verify()
}

internal fun EasyHandle.option(option: CURLoption, optionValue: CValuesRef<*>) {
    curl_easy_setopt(this, option, optionValue).verify()
}

internal fun EasyHandle.option(option: CURLoption, optionValue: String) {
    curl_easy_setopt(this, option, optionValue).verify()
}

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

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal fun Long.fromCurl(): HttpProtocolVersion = when (this) {
    CURL_HTTP_VERSION_1_0.toLong() -> HttpProtocolVersion.HTTP_1_0
    CURL_HTTP_VERSION_1_1.toLong() -> HttpProtocolVersion.HTTP_1_1
    CURL_HTTP_VERSION_2_0.toLong() -> HttpProtocolVersion.HTTP_2_0
    // old curl fallback
    else -> HttpProtocolVersion.HTTP_1_1
}
