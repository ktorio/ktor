package io.ktor.client.engine.curl

import io.ktor.client.engine.*

class CurlClientEngineConfig : HttpClientEngineConfig() {
    val workerResponseStandBy = 200 // Stand by for so long if the worker processor has any futures ready.
    val workerNextIterationDelay = 300L // Do something else for that long until the next worker processor iteration.
}
