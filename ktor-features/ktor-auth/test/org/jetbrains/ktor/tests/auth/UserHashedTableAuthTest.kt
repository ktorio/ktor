package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class UserHashedTableAuthTest {

    @Test
    fun testConfigInlined() {
        testSingle(UserHashedTableAuth(table = mapOf(
                "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
        )))
    }

    @Test
    fun testConfigParse() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")
        mapConfig.put("auth.users.0.hash", "VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=")
        testSingle(UserHashedTableAuth(mapConfig.config("auth")))
    }

    fun testSingle(hashedUserTable: UserHashedTableAuth) {
        withTestApplication {
            application.routing {
                authentication {
                    formAuthentication { hashedUserTable.authenticate(it) }
                }

                get("/") {
                    call.respondText("ok")
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/?user=test&password=bad-pass").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/?user=test&bad-user=bad-pass").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/?user=test&password=test").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }
}