package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import io.ktor.client.response.*
import kotlinx.io.charsets.Charset
import platform.Foundation.*

class CurlClientEngineConfig : HttpClientEngineConfig() {
    val curlMultiPerformPeriodicity = 100L
}
