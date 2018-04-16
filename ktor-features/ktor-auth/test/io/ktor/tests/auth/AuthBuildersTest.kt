package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import org.junit.Test
import java.util.*
import kotlin.test.*

class AuthBuildersTest {

    @Test
    fun testPrincipalsAccess() {
        val username = "testuser"

        withTestApplication {
            application.install(Authentication) {
                form { validate { c -> UserIdPrincipal(c.name) } }
            }

            application.routing {
                authenticate {
                    route("/") {

                        handle {
                            assertEquals(username, call.authentication.principal<UserIdPrincipal>()?.name)
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=$username&password=p")
            }
        }
    }

    @Test
    @Ignore
    fun testMultipleConfigurationsNested() {
        withTestApplication {
            application.install(Authentication) {
                form("first") { validate { c -> if (c.name == "first") UserIdPrincipal(c.name) else null } }
                basic("second") { validate { c -> if (c.name == "second") UserIdPrincipal(c.name) else null } }
            }

            application.routing {
                authenticate("first") {
                    authenticate("second") {
                        route("/both") {
                            handle {
                                assertEquals("first", call.authentication.principal<UserIdPrincipal>()?.name)
                                call.respondText("OK")
                            }
                        }
                        route("/{name}") {
                            handle {
                                assertEquals(
                                    call.parameters["name"],
                                    call.authentication.principal<UserIdPrincipal>()?.name
                                )
                                call.respondText("OK")
                            }
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/nobody") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=nobody&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/first") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/second") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/both") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testMultipleConfigurations() {
        withTestApplication {
            application.install(Authentication) {
                form("first") { validate { c -> if (c.name == "first") UserIdPrincipal(c.name) else null } }
                basic("second") { validate { c -> if (c.name == "second") UserIdPrincipal(c.name) else null } }
            }

            application.routing {
                authenticate("first", "second") {
                    route("/both") {
                        handle {
                            assertEquals("first", call.authentication.principal<UserIdPrincipal>()?.name)
                            call.respondText("OK")
                        }
                    }
                    route("/{name}") {
                        handle {
                            assertEquals(
                                call.parameters["name"],
                                call.authentication.principal<UserIdPrincipal>()?.name
                            )
                            call.respondText("OK")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/nobody") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=nobody&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/first") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/second") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/both") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testNoRouting() {
        withTestApplication {
            val auth = application.install(Authentication) {
                form { validate { UserIdPrincipal(it.name) } }
            }

            auth.interceptPipeline(application)

            application.intercept(ApplicationCallPipeline.Call) {
                call.respondText("OK")
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=any&password=")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testCompleteApplication() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestSession>("S")
            }
            application.install(Authentication) {
                session<TestSession>("S") {
                    challenge = SessionAuthChallenge.Ignore // a root session provider doesn't send any challenge
                    validate { UserIdPrincipal(it.name) } // but optionally provides an authenticated user
                }
                session<TestSession>("S_web") {
                    challenge = SessionAuthChallenge.Redirect { "/login" } // for web roots it does redirect to login
                    validate { UserIdPrincipal(it.name) } // unfortunately it does check twice
                }
                basic("B") {
                    realm = "test-app"
                    validate { UserIdPrincipal(it.name) }
                }
                form("F") {
                    validate { UserIdPrincipal(it.name) }
                }
            }
            application.routing {
                authenticate("S") {
                    get("/") {
                        call.respondText("Public index. ${call.principal<UserIdPrincipal>()?.name?.let { "Logged in as $it." } ?: "Not logged in."}")
                    }
                    route("/user") {
                        authenticate("S_web") {
                            get("profile") {
                                call.respondText("Profile for ${call.principal<UserIdPrincipal>()?.name}.")
                            }
                        }
                        authenticate("B") {
                            get("files/{name...}") {
                                call.respondText("File ${call.parameters["name"]} for user ${call.principal<UserIdPrincipal>()?.name}.")
                            }
                        }
                    }
                }
            }

            val serializedSession = autoSerializerOf<TestSession>().serialize(TestSession("tester"))
            fun TestApplicationRequest.addCookie() {
                addHeader(HttpHeaders.Cookie, "S=$serializedSession")
            }
            fun TestApplicationRequest.addBasicAuth() {
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("tester:".toByteArray())).render()
                )
            }

            on("Index should be available for everyone") {
                val call = handleRequest(HttpMethod.Get, "/")
                assertTrue { call.requestHandled }
                assertTrue { call.response.status()!!.isSuccess() }
                assertEquals("Public index. Not logged in.", call.response.content)
            }
            on("On the other side the index page should recognize logged in users") {
                val call = handleRequest(HttpMethod.Get, "/") {
                    addCookie()
                }

                assertTrue { call.requestHandled }
                assertTrue { call.response.status()!!.isSuccess() }
                assertEquals("Public index. Logged in as tester.", call.response.content)
            }
            on("User profile page should redirect to login page") {
                val call = handleRequest(HttpMethod.Get, "/user/profile")
                assertTrue { call.requestHandled }
                assertEquals("/login", call.response.headers[HttpHeaders.Location])
            }
            on("User profile page should be show for an authenticated user") {
                val call = handleRequest(HttpMethod.Get, "/user/profile") {
                    addCookie()
                }
                assertTrue { call.requestHandled }
                assertEquals("Profile for tester.", call.response.content)
            }
            on("An authenticated user can download files by a web browser") {
                val call = handleRequest(HttpMethod.Get, "/user/files/doc1.txt") {
                    addCookie()
                }
                assertTrue { call.requestHandled }
                assertEquals("File doc1.txt for user tester.", call.response.content)
            }
            on("A download manager or wget/curl tool could download file using basic auth") {
                val firstAttempt = handleRequest(HttpMethod.Get, "/user/files/doc1.txt")
                assertTrue { firstAttempt.requestHandled }
                // with no auth header we should get basic auth challenge
                assertEquals("Basic realm=test-app", firstAttempt.response.headers[HttpHeaders.WWWAuthenticate])

                // so a download tool should show a prompt so user can provide name and password
                // and retry with basic auth credentials
                val call = handleRequest(HttpMethod.Get, "/user/files/doc1.txt") {
                    addBasicAuth()
                }
                assertTrue { call.requestHandled }
                assertEquals("File doc1.txt for user tester.", call.response.content)
            }
        }
    }

    data class TestSession(val name: String)
}
