package io.ktor.client.engine.curl

import io.ktor.client.engine.*

class CurlClientEngineConfig : HttpClientEngineConfig() {
    /**
     * Stand by for so long if the worker processor has any futures ready.
     */
    var workerResponseStandBy: Int = 200

    /**
     * Do something else for that long until the next worker processor iteration.
     */
    var workerNextIterationDelay: Long = 300L
}
