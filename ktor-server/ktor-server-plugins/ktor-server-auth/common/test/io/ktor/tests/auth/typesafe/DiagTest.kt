@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test

class DiagTest {
    @Test
    fun `old authenticate nesting`() = testApplication {
        install(Authentication) {
            basic("old-basic") {
                validate { TestUser(it.name, "${it.name}@basic.com") }
            }
            bearer("old-bearer") {
                authenticate { if (it.token == "valid") TestUser("bearer", "b@b.com") else null }
            }
        }

        routing {
            authenticate("old-basic") {
                authenticate("old-bearer") {
                    get("/nested") {
                        val p = call.principal<TestUser>()
                        call.respondText("ok:${p?.name}")
                    }
                }
            }
        }

        // Only bearer
        val resp = client.get("/nested") {
            header(HttpHeaders.Authorization, bearerAuthHeader("valid"))
        }
        println("OLD-NESTED BEARER-ONLY: ${resp.status}")

        // Only basic
        val resp2 = client.get("/nested") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        println("OLD-NESTED BASIC-ONLY: ${resp2.status}")
    }
}
