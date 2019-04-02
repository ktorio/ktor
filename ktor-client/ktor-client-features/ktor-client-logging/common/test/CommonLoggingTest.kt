import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
import kotlin.test.*


class CommonLoggingTest {

    @Test
    fun testLogRequestWithException() = clientTest(MockEngine {
        throw CustomError("BAD REQUEST")
    }) {
        val testLogger = TestLogger()

        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            try {
                val response = client.get<HttpResponse>()
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")

            /**
             * Note: no way to join logger context => unpredictable logger output.
             */
        }
    }

    @Test
    fun testLogResponseWithException() = clientTest(MockEngine { request ->
        respondOk("Hello")
    }) {
        val testLogger = TestLogger()

        config {
            install("BadInterceptor") {
                responsePipeline.intercept(HttpResponsePipeline.Parse) {
                    throw CustomError("PARSE ERROR")
                }
            }

            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            val response = client.get<HttpResponse>()
            try {
                response.receive<String>()
            } catch (_: CustomError) {
                failed = true
            }

            response.close()
            response.coroutineContext[Job]!!.join()

            assertTrue(failed, "Exception is missing.")

            val dump = testLogger.dump()
            assertTrue(
                dump.contains("RESPONSE http://localhost/ failed with exception: CustomError: PARSE ERROR"),
                dump
            )
        }
    }
}

internal class CustomError(override val message: String) : Throwable()
