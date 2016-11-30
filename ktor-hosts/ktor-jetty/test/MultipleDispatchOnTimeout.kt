import org.jetbrains.ktor.application.BasicApplicationEnvironment
import org.jetbrains.ktor.application.call
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.application.respondWrite
import org.jetbrains.ktor.config.MapApplicationConfig
import org.jetbrains.ktor.host.applicationHostConfig
import org.jetbrains.ktor.host.connector
import org.jetbrains.ktor.jetty.embeddedJettyServer
import org.jetbrains.ktor.logging.SLF4JApplicationLog
import org.jetbrains.ktor.routing.Routing
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.servlet.ServletApplicationRequest
import org.junit.Test
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals


class MultipleDispatchOnTimeout {

    @Test
    fun foo(){
        val port = 44003
        val appHostConfig = applicationHostConfig { connector { this.port = port } }
        val appEnv = BasicApplicationEnvironment(javaClass.classLoader, SLF4JApplicationLog("KTorTest"), MapApplicationConfig())

        val callCount = AtomicInteger(0)

        val jetty = embeddedJettyServer(appHostConfig, appEnv) {
            install(Routing, {
                get("/foo") {
                    callCount.incrementAndGet()
                    val timeout: Long = (call.request as ServletApplicationRequest).servletRequest.asyncContext.timeout
                    println("Timeout is: $timeout")
                    Thread.sleep(timeout + 1000)
                    call.respondWrite {
                        write("A ok!")
                    }
                }
            })
        }
        try {
            jetty.start()

            Thread.sleep(1000)

            val result = URL("http://localhost:$port/foo").openConnection().inputStream.bufferedReader().readLine().let {
                it
            } ?: "<empty>"

            println("Got result: $result" )

            assertEquals(1, callCount.get())
            assertEquals("A ok!", result)
        } finally {
            jetty.stop()
        }
    }

}