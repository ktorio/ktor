package io.ktor.client.engine

import javax.net.ssl.*


open class HttpClientEngineConfig {
    var sslContext: SSLContext? = null
}