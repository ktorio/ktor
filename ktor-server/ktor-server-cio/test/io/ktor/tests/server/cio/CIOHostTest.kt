package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.testing.*

class CIOHostTest : HostTestSuite<CIOApplicationHost, CIOApplicationHost.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}