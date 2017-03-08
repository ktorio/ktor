import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import org.jetbrains.ktor.samples.httpbin.JsonResponse
import org.jetbrains.ktor.util.ValuesMap
import java.util.*

fun jsonOf(value: JsonResponse) : String
    = moshi.adapter(JsonResponse::class.java).toJson(value)



val moshi = Moshi.Builder()
    .add(MapAdapter())
    .build()


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

    @FromJson fun from(map: Map<String, String>) : ValuesMap = ValuesMap.Empty
}



