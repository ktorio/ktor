package org.jetbrains.ktor.tests.transform

import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.transform.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

class CallTransformTest {

    private val exec = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        exec.shutdown()
    }

    @Test
    fun syncTest() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.transform.register<Int> { it.toString() }
                    call.respond(777)
                }
            }

            handleRequest(HttpMethod.Get, "/").let { response ->
                assertEquals("777", response.response.content)
            }
        }
    }

    @Test
    fun asyncTest() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.transform.register<Int> { value ->
                        future { value.toString() }.await()
                    }
                    call.respond(777)
                }
            }

            handleRequest(HttpMethod.Get, "/").let { response ->
                assertEquals("777", response.response.content)
            }
        }
    }

    @Test
    fun asyncThenSync() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.transform.register<Int> { value ->
                        future { Wrapper(value + 1) }.await()
                    }

                    // TODO think of unsafe generics here
                    call.transform.register<Wrapper<Int>> { value ->
                        value.value.toString() + "s"
                    }

                    call.transform.register(Wrapper::class, { true }) { }

                    call.respond(777)
                }
            }

            handleRequest(HttpMethod.Get, "/").let { response ->
                assertEquals("778s", response.response.content)
            }
        }
    }

    private class Wrapper<out T>(val value: T)
}