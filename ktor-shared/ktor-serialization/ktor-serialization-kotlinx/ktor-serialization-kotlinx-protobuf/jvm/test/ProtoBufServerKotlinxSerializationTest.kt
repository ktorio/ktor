import io.ktor.http.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
class ProtoBufServerKotlinxSerializationTest : AbstractServerSerializationTest() {
    override val defaultContentType: ContentType = ContentType.Application.ProtoBuf
    override val customContentType: ContentType = ContentType.parse("application/protobuf")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        protobuf(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultProtoBuf.decodeFromByteArray(serializer, t)
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultProtoBuf.encodeToByteArray(serializer, any)
    }
}
