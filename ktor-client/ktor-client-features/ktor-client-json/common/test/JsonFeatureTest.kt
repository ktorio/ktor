package io.ktor.client.features.json

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class JsonFeatureTest {
    @Test
    fun testEmptyAllowedContentTypes() {
        assertFails {
            HttpClient {
                install(JsonFeature) {
                    allowedContentTypes = listOf()
                }
            }
        }

    }
}
