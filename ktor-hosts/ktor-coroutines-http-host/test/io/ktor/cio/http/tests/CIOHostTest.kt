package io.ktor.cio.http.tests

import io.ktor.cio.http.*
import io.ktor.testing.*

class CIOHostTest : HostTestSuite<CoroutinesHttpHost>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}