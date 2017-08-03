import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.servlet.*
import org.junit.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.test.*


class MultipleDispatchOnTimeout {

    private fun findFreePort() = ServerSocket(0).use { it.localPort }

    /**
     * We are testing that the servlet container does not trigger an extra error dispatch for calls that timeout from
     * the perspective of the servlet container. The fact that it does so is apparently specified here on this url:
     * https://docs.oracle.com/javaee/6/api/javax/servlet/AsyncContext.html
     */
    @Test
    fun `calls with duration longer than default timeout do not trigger a redispatch`() {
        val callCount = AtomicInteger(0)
        val port = findFreePort()
        val environment = applicationHostEnvironment {
            connector { this.port = port }
            log = SLF4JApplicationLog("ktor.test")
            module {
                install(Routing) {
                    get("/foo") {
                        callCount.incrementAndGet()
                        val timeout = Math.max((call.request as ServletApplicationRequest).servletRequest.asyncContext.timeout, 0)
                        // println("Timeout is: $timeout")
                        Thread.sleep(timeout + 1000)
                        call.respondWrite {
                            write("A ok!")
                        }
                    }
                }
            }
        }

        val jetty = embeddedServer(Jetty, environment)
        try {
            jetty.start()

            Thread.sleep(1000)

            val result = URL("http://localhost:$port/foo").openConnection().inputStream.bufferedReader().readLine().let {
                it
            } ?: "<empty>"

//            println("Got result: $result" )

            assertEquals(1, callCount.get())
            assertEquals("A ok!", result)
        } finally {
            jetty.stop(1, 5, TimeUnit.SECONDS)
        }
    }

}