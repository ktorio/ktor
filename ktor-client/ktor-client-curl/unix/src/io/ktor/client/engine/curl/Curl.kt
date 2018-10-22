package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import libcurl.curl_global_init
import libcurl.curl_global_cleanup
import libcurl.CURL_GLOBAL_ALL
import kotlinx.io.core.*

// This function is thread unsafe!
// The man page asks to run it once per program,
// while the program "is still single threaded", explicitly stating that
// it shoud not called while any other thread is running.
// See the curl_global_init(3) man page for details.
@SharedImmutable
private val curlGlobalInitReturnCode = curl_global_init(CURL_GLOBAL_ALL)

object Curl : HttpClientEngineFactory<HttpClientEngineConfig> {

    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        if (curlGlobalInitReturnCode != 0u) throw CurlEngineCreationException("curl_global_init() returned non-zero code")
        return CurlClientEngine(CurlClientEngineConfig().apply(block))
    }
}

fun CurlClient(): HttpClientEngineFactory<HttpClientEngineConfig> = Curl
