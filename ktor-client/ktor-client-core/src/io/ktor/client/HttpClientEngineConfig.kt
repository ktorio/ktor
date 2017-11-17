package io.ktor.client

import javax.net.ssl.*


open class HttpClientEngineConfig {
    var sslContext: SSLContext? = null
}