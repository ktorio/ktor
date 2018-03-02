package io.ktor.tests.server.testing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.coroutines.experimental.*
import kotlin.system.*
import kotlin.test.*

class TestApplicationEngineTest {
    @Test
    fun testCustomDispatcher() {
        fun CoroutineDispatcher.withDelay(delay: Delay): CoroutineDispatcher =
            object : CoroutineDispatcher(), Delay by delay {
                override fun isDispatchNeeded(context: CoroutineContext): Boolean =
                    this@withDelay.isDispatchNeeded(context)

                override fun dispatch(context: CoroutineContext, block: Runnable) =
                    this@withDelay.dispatch(context, block)
            }

        val delayLog = arrayListOf<String>()
        val delayTime = 10_000

        withTestApplication(
            moduleFunction = {
                routing {
                    get("/") {
                        delay(delayTime)
                        delay(delayTime)
                        call.respondText("OK")
                    }
                }
            },
            configure = {
                dispatcher = Unconfined.withDelay(object : Delay {
                    override fun scheduleResumeAfterDelay(
                        time: Long,
                        unit: TimeUnit,
                        continuation: CancellableContinuation<Unit>
                    ) {
                        // Run immediately and log it
                        val milliseconds = unit.toMillis(time)
                        delayLog += "Delay($milliseconds)"
                        continuation.resume(Unit)
                    }
                })
            }
        ) {
            handleRequest(HttpMethod.Get, "/").let { call ->
                val elapsedTime = measureTimeMillis {
                    call.awaitCompletion()
                }
                assertTrue { elapsedTime < (delayTime * 2) }
                assertTrue(call.requestHandled)
                assertEquals(
                    listOf("Delay($delayTime)", "Delay($delayTime)"),
                    delayLog
                )
            }
        }
    }
}
