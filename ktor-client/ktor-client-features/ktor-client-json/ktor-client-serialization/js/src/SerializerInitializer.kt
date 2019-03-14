import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*

val initializer = SerializerInitializer

object SerializerInitializer  {
    init {
        serializersStore += KotlinxSerializer()
    }
}
