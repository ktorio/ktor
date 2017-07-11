package org.jetbrains.ktor.tests.tomcat

import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tomcat.*
import java.util.logging.*

class TomcatHostTest : HostTestSuite<TomcatApplicationHost>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }
}