package io.ktor.server.host.cio.tests

import io.ktor.server.host.cio.*
import io.ktor.testing.*

class CIOHostTest : HostTestSuite<CoroutinesHttpHost, CoroutinesHttpHost.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}