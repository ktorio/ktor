import io.ktor.http.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class ProtoBufServerKotlinxSerializationTest : AbstractServerSerializationKotlinxTest() {
    override val defaultContentType: ContentType = ContentType.Application.ProtoBuf
    override val customContentType: ContentType = ContentType.parse("application/protobuf")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        protobuf(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return DefaultProtoBuf.decodeFromByteArray(serializer, t)
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return DefaultProtoBuf.decodeFromByteArray(listSerializer, t)
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return DefaultProtoBuf.encodeToByteArray(serializer, any)
    }

    @Ignore
    override fun testMap() {
    }

    @Ignore
    override fun testReceiveNullValue() {
    }

    @Ignore
    override fun testFlowNoAcceptUtf8() {
    }

    @Ignore
    override fun testFlowAcceptUtf16() {
    }
}
