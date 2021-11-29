/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import org.junit.*

class SinglePageTest {
    @Test
    fun testMainFile() = testApplication {
        install(SinglePage)

        val response = client.get("/index.html").bodyAsText()
        println(response)
    }
}
