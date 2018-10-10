import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.digest
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.isSuccess
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import org.junit.*
import java.security.*

class DigestTest : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(CIO, serverPort) {
        install(Authentication) {
            digest {
                val password = "Circle Of Life"
                digester = MessageDigest.getInstance("MD5")
                realm = "testrealm@host.com"

                userNameRealmPasswordDigestProvider = { userName, realm ->
                    when (userName) {
                        "missing" -> null
                        else -> digest(digester, "$userName:$realm:$password")
                    }
                }
            }
        }

        routing {
            authenticate {
                get("/") {
                    call.respondText("ok")
                }
            }
        }
    }

    @Test
    fun testAuth() = clientTest(io.ktor.client.engine.cio.CIO) {
        config {
            install(Auth) {
                digest {
                    username = "MyName"
                    password = "Circle Of Life"
                    realm = "testrealm@host.com"
                }
            }
        }
        test { client ->
            client.get<HttpResponse>(path = "/", port = serverPort).use {
                assert(it.status.isSuccess())
            }
        }
    }

    private fun digest(digester: MessageDigest, data: String): ByteArray {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }
}