/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.*
import kotlin.test.*

class OpenAPITest {

    @Test
    fun `default spec from file`() = testApplication {
        routing {
            openAPI("/swagger")
        }

        assertSwaggerHtmlReturned()
    }

    @Test
    fun `empty spec`() = testApplication {
        routing {
            openAPI("/swagger") {
                source = OpenAPISource.Empty
            }
        }

        assertSwaggerHtmlReturned()
    }

    @Test
    fun `multiple sources`() = testApplication {
        routing {
            openAPI("/swagger") {
                source = OpenAPISource.Empty
            }
        }

        assertSwaggerHtmlReturned()
    }

    private suspend fun ApplicationTestBuilder.assertSwaggerHtmlReturned() {
        client.get("/swagger").let { response ->
            assertEquals(200, response.status.value)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        }
    }
}
