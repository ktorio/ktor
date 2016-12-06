import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.routing.*
import org.junit.*
import java.net.*
import java.util.concurrent.*
import kotlin.test.*


class BlockingApplication : ApplicationModule() {
    override fun Application.install() {
        routing {
            get("/") { call ->
                println("Sleeping")
                TimeUnit.SECONDS.sleep(1)
                call.respondWrite(Charsets.ISO_8859_1) {
                    this.write("Help !")
                }
            }
        }
    }
}

class JettyDeadlockTest {

    @Test
    @Ignore
    // long integration test
    fun testLongRunningWithSleep() {
        val port = 44003

        val appHostConfig = applicationHostConfig { connector { this.port = port } }

        val appEnv = BasicApplicationEnvironment(javaClass.classLoader, SLF4JApplicationLog("KTorTest"), MapApplicationConfig(
                "ktor.application.class" to BlockingApplication::class.qualifiedName!!
        ))

        val jetty = JettyApplicationHost(appHostConfig, appEnv)
        jetty.start()

        val e = Executors.newCachedThreadPool()
        val q = LinkedBlockingQueue<String>()

        //println("starting")
        val conns = (0..2000).map { number ->
            e.submit(Callable<String> {
                try {
                    URL("http://localhost:$port/").openConnection().inputStream.bufferedReader().readLine().apply {
                        //println("$number says $this")
                    } ?: "<empty>"
                } catch (t: Throwable) {
                    "error: ${t.message}"
                }.apply {
                    q.add(this)
                }
            })
        }

        //println("Main thread is waiting for responses")

        TimeUnit.SECONDS.sleep(5)
        var attempts = 7

        fun dump() {
            val (valid, invalid) = conns.filter { it.isDone }.partition { it.get() == "Help !" }

            //println("Completed: ${valid.size} valid, ${invalid.size} invalid of ${valid.size + invalid.size} total [attempts $attempts]")
        }

        while (true) {
            dump()

            if (q.poll(5, TimeUnit.SECONDS) == null) {
                if (attempts <= 0) {
                    break
                }
                attempts--
            } else {
                attempts = 7
            }
        }

        dump()

        if (conns.any { !it.isDone }) {
            TimeUnit.SECONDS.sleep(500)
        }

        assertTrue { conns.all { it.isDone } }
    }


}