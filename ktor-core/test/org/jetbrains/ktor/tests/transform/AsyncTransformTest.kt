package org.jetbrains.ktor.tests.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.transform.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

class AsyncTransformTest {

    private val exec = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        exec.shutdown()
    }

    @Test
    fun smokeTest() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.transform.register<Int> { value ->
                        exec.submit { proceed(value.toString()) }
                        pause()
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
    fun suspendThenSync() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.transform.register<Int> { value ->
                        exec.submit { proceed(Wrapper(value + 1)) }
                        pause()
                    }

                    // TODO think of unsafe generics here
                    call.transform.register<Wrapper<Int>> { value ->
                        value.value.toString() + "s"
                    }

                    call.transform.register(Wrapper::class, { true }) { v -> }

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