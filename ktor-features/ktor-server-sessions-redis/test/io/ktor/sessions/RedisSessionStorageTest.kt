package io.ktor.sessions

import io.ktor.application.*
import io.ktor.experimental.client.redis.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Test
import kotlin.test.*

class RedisSessionStorageTest {
    @Test
    fun simple() {
        val logs = arrayListOf<String>()

        withTestApplication({
            val redis = object : Redis {
                override val context: Job = Job()
                override fun close() = Unit

                val values = LinkedHashMap<String, String>()

                override suspend fun execute(vararg args: Any?): Any? {
                    logs += "${args.toList()}"
                    return when (args.first().toString()) {
                        "set" -> "OK".also { values[args[1].toString()] = args[2].toString() }
                        "get" -> values[args[1].toString()]
                        "expire" -> "OK" // Do nothing!
                        else -> TODO("${args.toList()}")
                    }
                }
            }

            data class TestSession(val visits: Int = 0)

            install(Sessions) {
                val cookieName = "SESSION"
                val sessionStorage = RedisSessionStorage(redis, ttlSeconds = 17)
                cookie<TestSession>(cookieName, sessionStorage)
            }
            routing {
                get("/") {
                    val ses =
                        call.sessions.getOrNull<TestSession>() ?: TestSession()
                    call.sessions.set(TestSession(ses.visits + 1))
                    call.respondText("hello: $ses")
                }
            }
        }) {
            var cookie = ""

            with(handleRequest(HttpMethod.Get, "/")) {
                Assert.assertEquals(HttpStatusCode.OK, response.status())
                Assert.assertEquals("hello: TestSession(visits=0)", response.content)
                cookie = response.cookies["SESSION"]!!.value
            }

            with(handleRequest(HttpMethod.Get, "/") {
                addHeader("Cookie", "SESSION=$cookie")
            }) {
                Assert.assertEquals(HttpStatusCode.OK, response.status())
                Assert.assertEquals("hello: TestSession(visits=1)", response.content)
            }
        }

        assertEquals(
            """
                [set, session_ID, 7669736974733d2532336931]
                [expire, session_ID, 17]
                [get, session_ID]
                [expire, session_ID, 17]
                [set, session_ID, 7669736974733d2532336932]
                [expire, session_ID, 17]
            """.trimIndent(),
            logs.joinToString("\n").replace(Regex("session_\\w+"), "session_ID")
        )
    }
}
