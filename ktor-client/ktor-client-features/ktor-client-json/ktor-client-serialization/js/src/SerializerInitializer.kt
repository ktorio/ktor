import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.util.*

@InternalAPI
val initializer = SerializerInitializer

@InternalAPI
object SerializerInitializer  {
    init {
        serializersStore += KotlinxSerializer()
    }
}
