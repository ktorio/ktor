package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.io.*
import kotlin.test.*

class StaticContentTest {
    @Test
    fun testStaticContent() {
        withTestApplication {
            application.intercept { next ->
                val resolved = sequenceOf(
                        { resolveClasspathResource("", "org.jetbrains.ktor.tests.http") },
                        { resolveClasspathResource("", "java.util") },
                        { resolveLocalFile("", File("test")) }
                ).map { it() }.firstOrNull { it != null }

                if (resolved == null) {
                    next()
                } else {
                    response.send(resolved)
                }
            }

            handleRequest(HttpMethod.Get, "/StaticContentTest.class").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class2").let { result ->
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
        }
    }
}
