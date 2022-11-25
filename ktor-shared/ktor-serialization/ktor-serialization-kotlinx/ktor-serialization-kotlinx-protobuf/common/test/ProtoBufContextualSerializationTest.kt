import io.ktor.http.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.serialization.kotlinx.test.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
class ProtoBufContextualSerializationTest : AbstractContextualSerializationTest<ProtoBuf>() {
    override val defaultContentType: ContentType = ContentType.Application.ProtoBuf
    override val defaultSerializationFormat: ProtoBuf = DefaultProtoBuf

    override fun buildContextualSerializer(context: SerializersModule): ProtoBuf =
        ProtoBuf { serializersModule = context }

    override fun assertEquals(
        expectedAsJson: String,
        actual: ByteArray,
        format: ProtoBuf,
        serializer: KSerializer<*>
    ): Boolean {
        return expectedAsJson == actual.decodeToString()
    }
}
