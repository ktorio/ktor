package org.jetbrains.ktor.tests.auth

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
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
        testSingle(UserHashedTableAuth(ConfigFactory.parseString(
                """
                auth {
                    hashAlgorithm = "SHA-256",
                    salt = "ktor",
                    users = [{
                        name = "test",
                        hash = "VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0="
                    }
                    ]
                }
                """
        ).getConfig("auth")))
    }

    fun testSingle(hashedUserTable: UserHashedTableAuth) {
        withTestApplication {
            application.routing {
                auth {
                    formAuth()
                    verifyBatchTypedWith(hashedUserTable)

                    fail {
                        respondStatus(HttpStatusCode.Forbidden)
                    }
                }

                get("/") {
                    respondText("ok")
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.Forbidden, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/?user=test&password=bad-pass").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.Forbidden, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/?user=test&bad-user=bad-pass").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.Forbidden, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/?user=test&password=test").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }
}