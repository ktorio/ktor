package io.ktor.client.engine.curl

import kotlinx.cinterop.*
import libcurl.*

internal fun CURLMcode.code() {
    if (this != CURLM_OK) error("unexpected curl code: ${curl_multi_strerror(this)?.toKString()}")
}

internal fun CURLcode.code() {
    if (this != CURLE_OK) error("unexpected curl code: ${curl_easy_strerror(this)?.toKString()}")
}

internal fun COpaquePointer.option(option: CURLoption, vararg variadicArguments: Any?) {
    curl_easy_setopt(this, option, *variadicArguments)
        .code()
}

internal fun COpaquePointer.getinfo(info: CURLINFO, vararg variadicArguments: Any?) {
    curl_easy_getinfo(this, info, *variadicArguments)
        .code()
}