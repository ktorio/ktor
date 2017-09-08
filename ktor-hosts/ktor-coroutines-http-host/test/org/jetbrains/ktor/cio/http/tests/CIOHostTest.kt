package org.jetbrains.ktor.cio.http.tests

import org.jetbrains.ktor.cio.http.*
import org.jetbrains.ktor.testing.*

class CIOHostTest : HostTestSuite<CoroutinesHttpHost>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}