package io.ktor.client.engine.android

import io.ktor.client.engine.*
import java.net.*
import javax.net.ssl.*


/**
 * Configuration for [Android] client engine.
 */
class AndroidEngineConfig : HttpClientEngineConfig() {
    /**
     * Max milliseconds to establish an HTTP connection - default 10 seconds.
     * A value of 0 represents infinite.
     */
    var connectTimeout: Int = 100_000

    /**
     * Max milliseconds between TCP packets - default 10 seconds.
     * A value of 0 represents infinite.
     */
    var socketTimeout: Int = 100_000

    /**
     * Proxy address to use - default <code>{@link #openConnection java.net.URL:URL.openConnection}</code>
     */
    var proxy: Proxy? = null

    /**
     * https connection manipulator. inherited methods are not permitted.
     */
    var sslManager: (HttpsURLConnection) -> Unit = {}
}
