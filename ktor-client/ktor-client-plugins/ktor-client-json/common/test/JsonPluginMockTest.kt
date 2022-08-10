/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.client.plugins.json

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class JsonPluginMockTest {

    @Test
    fun testReceiveJsonLikeString() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        "{}",
                        headers = buildHeaders {
                            append(HttpHeaders.ContentType, "application/json")
                        }
                    )
                }
            }
            install(JsonPlugin) {
                serializer = MockSerializer
            }
        }

        val response = client.get("/").body<String>()
        assertEquals("{}", response)
    }
}

object MockSerializer : JsonSerializer {
    override fun write(data: Any, contentType: ContentType): OutgoingContent {
        error("Can't serialize $data")
    }

    override fun read(type: TypeInfo, body: Input): Any {
        error("can't read $type")
    }
}
