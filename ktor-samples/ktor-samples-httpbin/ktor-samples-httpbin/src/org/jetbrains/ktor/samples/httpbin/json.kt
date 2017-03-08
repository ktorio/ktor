import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import org.intellij.lang.annotations.Language
import org.jetbrains.ktor.request.MultiPartData
import org.jetbrains.ktor.request.PartData
import org.jetbrains.ktor.samples.httpbin.JsonResponse
import org.jetbrains.ktor.util.ValuesMap
import java.util.*

fun main(args: Array<String>) {
    @Language("JSON")
    val a = parseJson("""{"status": "success", "data": { "ok" : true }}""")
    println(a)
}
data class JsonObject(val data : Map<String, Any>)

fun jsonOf(value: JsonResponse) : String
    = moshi.adapter(JsonResponse::class.java).toJson(value)

fun parseJson(json: String) : JsonObject? = try {
    val map = moshiMapAdapter.fromJson(json) as Map<String, Any>
    JsonObject(map)
} catch (e: Exception) {
    println(e.message)
    null
}



val moshi = Moshi.Builder()
    .add(MapAdapter())
    .build()

private val moshiMapAdapter = moshi.adapter(Map::class.java).lenient()


private class MapAdapter {

    @ToJson
    fun toJson(parseMap: ValuesMap): Map<Any, Any> {
        val result = LinkedHashMap<Any, Any>()
        for ((key, value) in parseMap.entries()) {
            if (value.size == 1) {
                result.put(key, value[0])
            } else {
                result.put(key, value)
            }
        }
        return result
    }

    @ToJson
    fun multipart(multiPartData: MultiPartData?): Map<Any, Any>? {
        if (multiPartData == null) {
            return null
        }
        val result = LinkedHashMap<Any, Any>()
        for (part in multiPartData.parts) {
            when (part) {
                is PartData.FormItem -> result.put(part.partName!!, part.value)
                is PartData.FileItem -> result.put(part.partName!!, "A file of type ${part.contentType}")
            }
        }
        return result
    }

    @FromJson fun from(map: Map<String, String>) : ValuesMap = ValuesMap.Empty

    @FromJson fun fromMultipart(map: Map<String, String>) : MultiPartData? = null
}



