package io.ktor.client.engine.android

import io.ktor.client.engine.*


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

}