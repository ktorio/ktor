import io.ktor.client.engine.mock.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class CookiesTest {

    @Test
    fun compatibilityTest() = clientTest(MockEngine {
        assertEquals("*/*", headers[HttpHeaders.Accept])
        val rawCookies = headers[HttpHeaders.Cookie]!!
        assertFalse(rawCookies.contains("x-enc"))

        assertEquals(1, headers.getAll(HttpHeaders.Cookie)?.size!!)
        val cookies = parseClientCookiesHeader(rawCookies)

        assertEquals(2, cookies.size)
        assertEquals("1,2,3,4".encodeURLParameter(), cookies["first"])
        assertEquals("abc", cookies["second"])

        responseOk()
    }) {

        config {
            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie("//localhost", Cookie("first", "1,2,3,4"))
                        addCookie("http://localhost", Cookie("second", "abc"))
                    }
                }
            }
        }

        test { client ->
            client.get<HttpResponse>()
        }
    }
}
