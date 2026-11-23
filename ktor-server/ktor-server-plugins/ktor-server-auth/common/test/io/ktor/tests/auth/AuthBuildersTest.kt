/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.delay
import kotlinx.serialization.*
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AuthBuildersTest {

    @Test
    fun `nested authenticate runs each provider at the outermost block that lists it`() = testApplication {
        class BearerUser(val name: String)
        class FormUser(val name: String)

        val seenByPlugin = mutableListOf<String?>()
        val principalRecorder = createRouteScopedPlugin("PrincipalRecorder") {
            onCallValidators { call -> if (!call.isHandled) seenByPlugin.add(call.principal<BearerUser>()?.name) }
        }

        install(Authentication) {
            bearer("bearer") {
                authenticate { credential -> BearerUser(credential.token).takeIf { credential.token == "token" } }
            }
            form("form") { validate { c -> FormUser(c.name).takeIf { c.name == "user" } } }
        }

        routing {
            authenticate("bearer", strategy = AuthenticationStrategy.Required) {
                route("/") {
                    install(principalRecorder)
                    // "bearer" is repeated here but stays owned by the outer block.
                    authenticate("bearer", "form", strategy = AuthenticationStrategy.Required) {
                        post("/both") {
                            call.respondText(
                                "${call.principal<BearerUser>()?.name}:${call.principal<FormUser>()?.name}"
                            )
                        }
                    }
                }
            }
        }

        val both = client.post("/both") {
            bearerAuth("token")
            formCredentials("user")
        }
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals("token:user", both.bodyAsText())
        assertEquals(listOf<String?>("token"), seenByPlugin)

        val missingForm = client.post("/both") { bearerAuth("token") }
        assertEquals(HttpStatusCode.Unauthorized, missingForm.status)
        assertEquals(listOf<String?>("token", "token"), seenByPlugin)

        val missingBearer = client.post("/both") {
            formCredentials("user")
        }
        assertEquals(HttpStatusCode.Unauthorized, missingBearer.status)
        assertEquals(listOf<String?>("token", "token"), seenByPlugin, "Plugin must not run when outer auth fails")
    }

    @Test
    fun `nested authenticate resolves provider registered after outer authenticate`() = testApplication {
        class OuterUser(val name: String)
        class InnerUser(val name: String)

        install(Authentication) {
            bearer("outer") {
                authenticate { credential -> OuterUser(credential.token).takeIf { credential.token == "token" } }
            }
        }

        routing {
            authenticate("outer", strategy = AuthenticationStrategy.Required) {
                application.authentication {
                    form("inner") { validate { c -> InnerUser(c.name).takeIf { c.name == "user" } } }
                }
                authenticate("inner", strategy = AuthenticationStrategy.Required) {
                    post("/both") {
                        call.respondText("${call.principal<OuterUser>()?.name}:${call.principal<InnerUser>()?.name}")
                    }
                }
            }
        }

        val response = client.post("/both") {
            bearerAuth("token")
            formCredentials("user")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("token:user", response.bodyAsText())
    }

    @Test
    fun testPrincipalsAccess() = testApplication {
        val username = "testuser"

        install(Authentication) {
            form { validate { c -> UserIdPrincipal(c.name) } }
        }

        routing {
            authenticate {
                route("/") {
                    handle {
                        call.respondText(call.authentication.principal<UserIdPrincipal>()?.name.toString())
                    }
                }
            }
        }

        val response = client.post("/") { formCredentials(username) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(username, response.bodyAsText())
    }

    @Test
    fun `required providers of nested blocks must all succeed`() = testStrategies(
        withoutCredentials = rejected,
        withFirst = rejected,
        withSecond = rejected,
        withBoth = principals("first", "second"),
        { endpoint ->
            authenticate("first", strategy = AuthenticationStrategy.Required) {
                authenticate("second", strategy = AuthenticationStrategy.Required) { endpoint() }
            }
        },
        // Listing a required provider again with a weaker strategy does not downgrade it.
        { endpoint ->
            authenticate("first", strategy = AuthenticationStrategy.Required) {
                authenticate("first") {
                    authenticate("second", strategy = AuthenticationStrategy.Required) { endpoint() }
                }
            }
        },
    )

    @Test
    fun testMultipleRequiredConfigurationsAccessByName() = testApplication {
        class UserNamePrincipal(val name: String)

        install(Authentication) {
            form("first") { validate { c -> if (c.name == "first") UserNamePrincipal(c.name) else null } }
            basic("second") { validate { c -> if (c.name == "second") UserNamePrincipal(c.name) else null } }
        }

        routing {
            authenticate("first", strategy = AuthenticationStrategy.Required) {
                authenticate("second", strategy = AuthenticationStrategy.Required) {
                    post("/both") {
                        call.respondText(
                            call.principal<UserNamePrincipal>("first")?.name +
                                " " + call.principal<UserNamePrincipal>("second")?.name
                        )
                    }
                }
            }
        }

        val response = client.post("/both") {
            addBasicAuth("second")
            formCredentials("first")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("first second", response.bodyAsText())
    }

    @Test
    fun `non-required providers are tried in order until one succeeds`() = testStrategies(
        withoutCredentials = rejected,
        withFirst = principals("first"),
        withSecond = principals("second"),
        withBoth = principals("first"),
        { endpoint -> authenticate("first", "second") { endpoint() } },
        // Splitting the providers into nested blocks does not change the outcome.
        { endpoint ->
            authenticate("first") {
                authenticate("second") { endpoint() }
            }
        },
        { endpoint ->
            authenticate("first", strategy = AuthenticationStrategy.Optional) {
                authenticate("second") { endpoint() }
            }
        },
    )

    @Test
    fun `non-required provider is not tried after required provider succeeds`() = testStrategies(
        withoutCredentials = rejected,
        withFirst = principals("first"),
        withSecond = rejected,
        withBoth = principals("first"),
        { endpoint ->
            authenticate("first", strategy = AuthenticationStrategy.Required) {
                authenticate("second") { endpoint() }
            }
        },
    )

    @Test
    fun `required provider of inner block runs before optional provider of outer block`() = testStrategies(
        withoutCredentials = rejected,
        withFirst = rejected,
        withSecond = principals("second"),
        withBoth = principals("second"),
        { endpoint ->
            authenticate("first", strategy = AuthenticationStrategy.Optional) {
                authenticate("second", strategy = AuthenticationStrategy.Required) { endpoint() }
            }
        },
    )

    @Test
    fun `anonymous call is allowed only when every nested block is optional`() = testStrategies(
        withoutCredentials = anonymous,
        withFirst = principals("first"),
        withSecond = principals("second"),
        withBoth = principals("first"),
        { endpoint ->
            authenticate("first", strategy = AuthenticationStrategy.Optional) {
                authenticate("second", strategy = AuthenticationStrategy.Optional) { endpoint() }
            }
        },
    )

    @Test
    fun testMultipleConfigurationsInstallLevel() = testApplication {
        install(Authentication) {
            basic("first") { validate { c -> if (c.name == "first") UserIdPrincipal(c.name) else null } }
            basic("second") { validate { c -> if (c.name == "second") UserIdPrincipal(c.name) else null } }
        }

        routing {
            authenticate("first") {
                post("/first") {
                    call.respondText("From: ${call.principal<UserIdPrincipal>()!!.name}")
                }
            }
            route("/second") {
                authenticate("second") {
                    post {
                        call.respondText("From: ${call.principal<UserIdPrincipal>()!!.name}")
                    }
                }
            }
        }

        client.post("/first") {
            addBasicAuth("first")
        }.let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("From: first", it.bodyAsText())
        }

        client.post("/second") {
            addBasicAuth("second")
        }.let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("From: second", it.bodyAsText())
        }

        client.post("/first") {
            addBasicAuth("second")
        }.let {
            assertEquals(HttpStatusCode.Unauthorized, it.status)
        }
        client.post("/second") {
            addBasicAuth("first")
        }.let {
            assertEquals(HttpStatusCode.Unauthorized, it.status)
        }
    }

    @Test
    fun testNoRouting() = testApplication {
        install(Authentication) {
            form { validate { UserIdPrincipal(it.name) } }
        }

        @OptIn(InternalAPI::class)
        install(AuthenticationInterceptors)

        application {
            intercept(ApplicationCallPipeline.Call) {
                call.respondText("OK")
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/").status)

        client.post("/") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=any&password=")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testModifyingAuthentication() = testApplication {
        application {
            authentication {
                basic("1") {
                    validate { cred -> cred.name.takeIf { it == "aaa" }?.let { UserIdPrincipal(it) } }
                }
            }

            on("add a new auth method") {
                authentication {
                    form("2") {
                        validate { cred -> cred.name.takeIf { it == "bbb" }?.let { UserIdPrincipal(it) } }
                    }
                }
            }

            on("auth method name conflict") {
                authentication {
                    assertFails {
                        basic("2") {}
                    }
                }
            }
        }

        routing {
            authenticate("1", "2") {
                get("/") {
                    call.respondText(call.principal<UserIdPrincipal>()?.name ?: "?")
                }
            }
        }

        on("attempt to auth") {
            fun HttpRequestBuilder.addFormAuth(name: String) {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=$name&password=")
            }

            on("try first auth provider") {
                val call = client.get("/") {
                    addBasicAuth("aaa")
                }
                assertEquals("aaa", call.bodyAsText())
            }
            on("try second auth provider") {
                val call = client.get("/") {
                    addFormAuth("bbb")
                }
                assertEquals("bbb", call.bodyAsText())
            }
            on("try invalid user name") {
                val call = client.get("/") {
                    addBasicAuth("unknown")
                }
                assertEquals(HttpStatusCode.Unauthorized, call.status)
            }
        }
    }

    @Test
    fun testAuthenticateOptionally() = testApplication {
        application {
            authentication {
                basic {
                    validate { cred -> cred.name.takeIf { it == "aaa" }?.let { UserIdPrincipal(it) } }
                }
            }
            routing {
                authenticate(optional = true) {
                    get("/auth") {
                        call.respond("OK:${call.authentication.principal<UserIdPrincipal>()?.name}")
                    }
                }
            }
        }
        on("try call with authentication") {
            val call = client.get("/auth") {
                addBasicAuth("aaa")
            }
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("OK:aaa", call.bodyAsText())
        }
        on("try call with bad authentication") {
            val call = client.get("/auth") {
                addBasicAuth("unknown")
            }
            assertEquals(HttpStatusCode.Unauthorized, call.status)
        }
        on("try call without authentication") {
            val call = client.get("/auth") {}
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("OK:null", call.bodyAsText())
        }
    }

    @Test
    fun testAuthProviderFailureNoChallenge() = testApplication {
        class CustomErrorCause : AuthenticationFailedCause.Error("custom error")

        val failures = mapOf<String, suspend () -> AuthenticationFailedCause>(
            "error" to { AuthenticationFailedCause.Error("test") },
            "inheritance" to { CustomErrorCause() },
            "suspend" to {
                delay(10.milliseconds)
                AuthenticationFailedCause.Error("test")
            },
        )

        application {
            authentication {
                for ((name, cause) in failures) {
                    provider(name) {
                        authenticate { context -> context.error(this, cause()) }
                    }
                }
            }
            routing {
                for (name in failures.keys) {
                    authenticate(name) {
                        get("/fail-$name") { fail("shouldn't reach here") }
                    }
                    authenticate(name, optional = true) {
                        get("/pass-$name") { call.respondText("OK") }
                    }
                }
            }
        }

        for (name in failures.keys) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/fail-$name").status, name)

            val optional = client.get("/pass-$name")
            assertEquals(HttpStatusCode.OK, optional.status, name)
            assertEquals("OK", optional.bodyAsText(), name)
        }
    }

    @Test
    fun testAuthProviderFailureWithChallenge() = testApplication {
        application {
            authentication {
                provider("custom") {
                    authenticate { context ->
                        context.challenge(this, AuthenticationFailedCause.Error("test")) { challenge, call ->
                            call.respondText("Challenge")
                            challenge.complete()
                        }
                    }
                }
            }
            routing {
                authenticate("custom") {
                    get("/fail") {
                        fail("shouldn't reach here")
                    }
                }
                authenticate("custom", optional = true) {
                    get("/pass") {
                        call.respondText("OK")
                    }
                }
            }
        }

        client.get("/fail").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("Challenge", call.bodyAsText())
        }

        client.get("/pass").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testAuthDoesntChangeRoutePriority() = testApplication {
        application {
            install(Authentication) {
                form { validate { c -> UserIdPrincipal(c.name) } }
            }

            routing {
                get("/bar:{baz}") {
                    call.respondText("bar")
                }
                authenticate {
                    get("/{baz}") {
                        call.respondText("baz")
                    }
                }
                get("/foo:{baz}") {
                    call.respondText("foo")
                }
            }
        }

        client.get("/foo:asd") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=username&password=p")
        }.let { call ->
            assertEquals("foo", call.bodyAsText())
        }
        client.get("/bar:asd") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=username&password=p")
        }.let { call ->
            assertEquals("bar", call.bodyAsText())
        }
        client.get("/baz") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=username&password=p")
        }.let { call ->
            assertEquals("baz", call.bodyAsText())
        }
    }

    @Test
    fun testAuthInterceptorKeepCallParameters() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Monitoring) {
                call.authentication
            }

            install(Authentication) {
                basic {
                    validate { cred ->
                        assertEquals("id1", parameters["id"])
                        cred.name.takeIf { it == "aaa" }?.let { UserIdPrincipal(it) }
                    }
                }
            }

            routing {
                authenticate {
                    get("/{id}") {
                        call.respond("Id: ${call.parameters["id"]}")
                    }
                }
            }
        }

        client.get("/id1") {
            headers.append(
                HttpHeaders.Authorization,
                HttpAuthHeader.Single("basic", Base64.encode("aaa:bbb".encodeToByteArray())).render()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Id: id1", response.bodyAsText())
        }
    }

    @Test
    fun testCompleteApplication() = testApplication {
        install(Sessions) {
            cookie<TestSession>("S")
        }
        install(Authentication) {
            session<TestSession>("S") {
                challenge {} // a root session provider doesn't send any challenge
                validate { UserIdPrincipal(it.name) } // but optionally provides an authenticated user
            }
            session<TestSession>("S_web") {
                challenge("/login") // for web roots it does redirect to login
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
        routing {
            authenticate("S") {
                get("/") {
                    val logText = call.principal<UserIdPrincipal>()?.name
                        ?.let { "Logged in as $it." } ?: "Not logged in."
                    call.respondText("Public index. $logText")
                }
                route("/user") {
                    authenticate("S_web") {
                        get("profile") {
                            call.respondText("Profile for ${call.principal<UserIdPrincipal>()?.name}.")
                        }
                    }
                    authenticate("B") {
                        get("files/{name...}") {
                            call.respondText(
                                "File ${call.parameters["name"]} for user " +
                                    "${call.principal<UserIdPrincipal>()?.name}."
                            )
                        }
                    }
                }
                get("/login") {
                    val user = call.principal<UserIdPrincipal>()
                    if (user != null) {
                        call.respondRedirect("/")
                    } else {
                        call.respondText("Login form goes here.")
                    }
                }
                authenticate("F") {
                    post("/login") {
                        val user = call.principal<UserIdPrincipal>()
                        assertNotNull(user)
                        call.sessions.set(TestSession(user.name))
                        call.respondText("Logged in successfully as ${user.name}.")
                    }
                }
            }
        }

        val serializedSession = defaultSessionSerializer<TestSession>().serialize(
            TestSession("tester")
        )
        val sessionCookieContent = "S=$serializedSession"
        fun HttpRequestBuilder.addCookie() {
            header(HttpHeaders.Cookie, sessionCookieContent)
        }

        fun HttpRequestBuilder.addFormAuth(name: String, pass: String) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=$name&password=$pass")
        }

        val client = createClient {
            followRedirects = false
        }

        val response1 = client.get("/")
        assertTrue { response1.status.isSuccess() }
        assertEquals("Public index. Not logged in.", response1.bodyAsText())

        val response2 = client.get("/") {
            addCookie()
        }
        assertTrue { response2.status.isSuccess() }
        assertEquals("Public index. Logged in as tester.", response2.bodyAsText())

        val response3 = client.get("/user/profile")
        assertEquals("/login", response3.headers[HttpHeaders.Location])

        val response4 = client.get("/user/profile") {
            addCookie()
        }
        assertEquals("Profile for tester.", response4.bodyAsText())

        val response5 = client.get("/login") {
            addCookie()
        }
        assertEquals(HttpStatusCode.Found, response5.status)

        val response6 = client.get("/login")
        assertEquals("Login form goes here.", response6.bodyAsText())

        val response7 = client.post("/login") {
            addFormAuth("tester", "")
        }
        val cookies = response7.headers[HttpHeaders.SetCookie]?.let { parseServerSetCookieHeader(it) }
        assertNotNull(cookies, "Set-Cookie should be sent")
        assertEquals(serializedSession, cookies.value)
        assertEquals("Logged in successfully as tester.", response7.bodyAsText())

        val response8 = client.get("/user/files/doc1.txt") {
            addCookie()
        }
        assertEquals("File doc1.txt for user tester.", response8.bodyAsText())

        val firstAttempt = client.get("/user/files/doc1.txt")
        // with no auth header we should get basic auth challenge
        assertEquals(
            "Basic realm=test-app, charset=UTF-8",
            firstAttempt.headers[HttpHeaders.WWWAuthenticate]
        )

        // so a download tool should show a prompt so user can provide name and password
        // and retry with basic auth credentials
        val response9 = client.get("/user/files/doc1.txt") {
            addBasicAuth()
        }
        assertEquals("File doc1.txt for user tester.", response9.bodyAsText())
    }

    private class FirstUser(val name: String)
    private class SecondUser(val name: String)

    /** The request is answered with 401. */
    private val rejected: Set<String>? = null

    /** The request reaches the handler without any principal. */
    private val anonymous: Set<String> = emptySet()

    /** The request reaches the handler with principals of exactly these providers. */
    private fun principals(vararg providers: String): Set<String> = providers.toSet()

    /**
     * Checks that every one of [combinations] of `authenticate` blocks treats credentials the same way, using a form
     * provider `first` and a basic provider `second`.
     *
     * Each expectation is [rejected], [anonymous] or [principals] for a request carrying the corresponding valid
     * credentials. A request with invalid credentials must always be rejected.
     */
    private fun testStrategies(
        withoutCredentials: Set<String>?,
        withFirst: Set<String>?,
        withSecond: Set<String>?,
        withBoth: Set<String>?,
        vararg combinations: Route.(endpoint: Route.() -> Unit) -> Unit
    ) = testApplication {
        install(Authentication) {
            form("first") { validate { c -> if (c.name == "first") FirstUser(c.name) else null } }
            basic("second") { validate { c -> if (c.name == "second") SecondUser(c.name) else null } }
        }

        routing {
            combinations.forEachIndexed { index, combination ->
                route("/$index") {
                    combination {
                        post {
                            val principals = listOfNotNull(
                                call.principal<FirstUser>()?.name,
                                call.principal<SecondUser>()?.name
                            )
                            call.respondText(principals.joinToString(","))
                        }
                    }
                }
            }
        }

        suspend fun outcome(path: String, form: String? = null, basic: String? = null): Set<String>? {
            val response = client.post(path) {
                if (form != null) formCredentials(form)
                if (basic != null) addBasicAuth(basic)
            }
            return when (response.status) {
                HttpStatusCode.OK -> response.bodyAsText().split(",").filter { it.isNotEmpty() }.toSet()
                HttpStatusCode.Unauthorized -> rejected
                else -> fail("Unexpected status ${response.status}")
            }
        }

        for (index in combinations.indices) {
            val path = "/$index"
            assertEquals(rejected, outcome(path, form = "nobody"), "combination $index: invalid credentials")
            assertEquals(withoutCredentials, outcome(path), "combination $index: no credentials")
            assertEquals(withFirst, outcome(path, form = "first"), "combination $index: only first")
            assertEquals(withSecond, outcome(path, basic = "second"), "combination $index: only second")
            assertEquals(withBoth, outcome(path, form = "first", basic = "second"), "combination $index: both")
        }
    }

    private fun HttpRequestBuilder.addBasicAuth(name: String = "tester") {
        header(
            HttpHeaders.Authorization,
            HttpAuthHeader.Single("basic", Base64.encode("$name:".encodeToByteArray())).render()
        )
    }

    @Serializable
    data class TestSession(val name: String)
}
