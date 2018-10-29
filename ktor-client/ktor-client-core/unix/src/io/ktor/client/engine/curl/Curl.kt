package io.ktor.client.engine.curl

import io.ktor.client.engine.*

external fun CurlClient(): HttpClientEngineFactory<HttpClientEngineConfig>
