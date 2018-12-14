import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
data class HttpBinData(val name: String)

@Test
fun test() = runBlocking {
    val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer().apply {
                register(HttpBinData.serializer())
            }
        }
    }

    client.get<HttpBinData>(urlString = "http://httpbin.org/get")
    client.put<HttpBinData>(urlString = "http://httpbin.org/put") {
        body = "PUUUUT"
    }

    client.post<HttpBinData>(urlString = "http://httpbin.org/post") {
        body = "POOST"
    }

    client.close()
}
