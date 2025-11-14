/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.tests.*
import java.net.http.HttpClient
import kotlin.test.Ignore
import kotlin.test.Test

class JavaHttp2Test : Http2Test<JavaHttpConfig>(Java) {
    override fun JavaHttpConfig.enableHttp2() {
        protocolVersion = HttpClient.Version.HTTP_2
    }

    @Ignore // KTOR-8947 Java: Headers contain ":status" pseudo header with HTTP/2
    @Test
    override fun `test pseudo headers are ignored`() {}
}
