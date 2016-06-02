package org.jetbrains.ktor.tests.tomcat

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*
import org.jetbrains.ktor.tomcat.*
import org.junit.*

class TomcatStaticTest : HostTestSuite() {

    override fun createServer(port: Int, block: Routing.() -> Unit): ApplicationHost {
        return embeddedTomcatServer(port, application = block)
    }
}