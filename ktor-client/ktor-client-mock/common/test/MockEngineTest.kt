import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class MockEngineTest {

    @Test
    fun testHistoryIteration() {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("Test")
                }
            }
        }
        val engine = client.engine as MockEngine
        val count = engine.requestHistory
            .map(HttpRequestData::url)
            .map(Url::toString)
            .count()

        assertEquals(0, count)
    }
}
