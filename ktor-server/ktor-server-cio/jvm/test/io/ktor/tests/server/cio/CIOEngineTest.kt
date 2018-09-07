package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.testing.*

class CIOEngineTest : EngineTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
