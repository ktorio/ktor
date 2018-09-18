import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlinx.cinterop.*
import kotlin.native.concurrent.*
import io.ktor.client.engine.curl.temporary.anEventLoop

suspend fun describe(call: HttpClientCall): String {
    var text = ""
    do {
        val line = call.response.content.readUTF8Line(1000)
        text += "$line\n"
    } while(line != null)
    return "${call.request.url} says ${text}\n${call.response.status}\n${call.response.headers}\nver ${call.response.version}"
}

fun foo(context: CoroutineContext, callback: (String) -> Unit) {

    launch(context) {

        val client = HttpClient(Curl)

        val call1 = client.call("https://httpbin.org/get") {
            method = HttpMethod.Get
        }
        val call2 = client.call("https://httpbin.org/put") {
            method = HttpMethod.Put
            body = "mike check"
        }

        val call3 = client.call("https://httpbin.org/post") {
            method = HttpMethod.Post
            body = "one two three"
        }
        client.close()

        callback(describe(call1))
        callback(describe(call2))
        callback(describe(call3))
    }

}

val UI = anEventLoop

fun main() =  UI.run {
    runBlocking {
        foo(UI) {
            println(it)
        }
    }
}
