import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.*
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
            digest("digest") {
                val password = "Circle Of Life"
                algorithmName = "MD5"
                realm = "testrealm@host.com"

                userNameRealmPasswordDigestProvider = { userName, realm ->
                    digest(MessageDigest.getInstance(algorithmName), "$userName:$realm:$password")
                }
            }

            basic("basic") {
                validate { credential ->
                    check("MyUser" == credential.name)
                    check("1234" == credential.password)
                    UserIdPrincipal("MyUser")
                }
            }
        }

        routing {
            authenticate("basic") {
                get("/basic") {
                    call.respondText("ok")
                }
            }
            authenticate("digest") {
                get("/digest") {
                    call.respondText("ok")
                }
            }
        }
    }

    @Test
    fun testDigestAuth() = clientTest(io.ktor.client.engine.cio.CIO) {
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
            client.get<HttpResponse>(path = "/digest", port = serverPort).use {
                assert(it.status.isSuccess())
            }
        }
    }

    @Test
    fun testBasicAuth() = clientTest(io.ktor.client.engine.cio.CIO) {
        config {
            install(Auth) {
                basic {
                    username = "MyUser"
                    password = "1234"
                }
            }
        }

        test { client ->
            client.get<String>(path = "/basic", port = serverPort)
        }
    }

    private fun digest(digester: MessageDigest, data: String): ByteArray {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }
}
