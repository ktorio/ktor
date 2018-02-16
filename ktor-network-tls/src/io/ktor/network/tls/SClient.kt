package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.net.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.system.*

fun main(args: Array<String>) {
    var host = "localhost"
    var port = 443
    var customManager: X509TrustManager? = null
    var serverName: String? = null

    val it = args.iterator()
    while (it.hasNext()) {
        val arg = it.next()

        if (arg.startsWith("-")) {
            when (arg.substringBefore("=")) {
                "-h", "-?", "-help", "--help" -> printHelp()
                "-k", "--insecure" -> customManager = TrustAllManager
                "-sname" -> serverName = arg.substringAfter("=", "")
                else -> {
                    System.err.println("Invalid option $arg")
                    printHelp()
                    exitProcess(1)
                }
            }
        } else {
            host = arg.substringBefore(":")
            port = arg.substringAfter(":", "").takeIf { it.isNotEmpty() }?.toInt() ?: 443
            if (it.hasNext()) {
                System.err.println("Unexpected extra arguments: ${it.asSequence().joinToString(" ")}")
                printHelp()
                exitProcess(1)
            }
        }
    }

    val remoteAddress = InetSocketAddress(host, port)
    if (serverName != null && serverName.isEmpty()) {
        serverName = host
    }

    runBlocking {
        ActorSelectorManager(ioCoroutineDispatcher).use { selector ->
            aSocket(selector).tcp().connect(remoteAddress).tls(customManager, serverName).use { socket ->
                val input = socket.openReadChannel()
                val output = socket.openWriteChannel()

                launch(CommonPool) {
                    try {
                        val buffer = ByteArray(8192)
                        while (true) {
                            val rc = System.`in`.read(buffer)
                            if (rc == -1) break
                            output.writeFully(buffer, 0, rc)
                            output.flush()
                        }
                    } finally {
                        output.close()
                    }
                }

                val bb = ByteBuffer.allocate(8192)
                while (true) {
                    val rc = input.readAvailable(bb)
                    if (rc == -1) break
                    bb.flip()
                    System.out.write(bb.array(), bb.arrayOffset() + bb.position(), rc)
                    System.out.flush()
                }
            }
        }
    }
}

private fun printHelp() {
    println("java ... SClientKt [-h|-?|-help|--help] [-k|--insecure] [-sname|-sname=servername] host[:port]")
}

private object TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate>? {
        return null
    }
}