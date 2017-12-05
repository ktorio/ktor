package io.ktor.client.engine

import kotlinx.coroutines.experimental.*
import javax.net.ssl.*


open class HttpClientEngineConfig {
    var sslContext: SSLContext? = null
    var dispatcher: CoroutineDispatcher? = null
}