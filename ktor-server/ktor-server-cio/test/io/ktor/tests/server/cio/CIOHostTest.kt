package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.testing.*

class CIOHostTest : HostTestSuite<CoroutinesHttpHost, CoroutinesHttpHost.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}