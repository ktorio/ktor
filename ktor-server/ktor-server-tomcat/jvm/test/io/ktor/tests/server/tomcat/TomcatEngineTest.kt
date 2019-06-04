/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.tomcat

import io.ktor.server.testing.*
import io.ktor.server.tomcat.*
import org.junit.*
import java.util.logging.*

class TomcatEngineTest : EngineTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }

    @Ignore
    @Test
    override fun testUpgrade() {
        super.testUpgrade()
    }
}
