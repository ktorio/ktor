import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.protobuf.*
import kotlinx.serialization.*
import org.junit.Ignore
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class ProtoBufClientKotlinxSerializationTest : AbstractClientContentNegotiationTest() {
    private val converter = KotlinxSerializationConverter(DefaultProtoBuf)
    override val defaultContentType: ContentType = ContentType.Application.ProtoBuf
    override val customContentType: ContentType = ContentType.parse("text/protobuf")
    override val webSocketsConverter: WebsocketContentConverter =
        KotlinxWebsocketSerializationConverter(DefaultProtoBuf)

    override fun ContentNegotiation.Config.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    @Test
    @Ignore
    override fun testSerializeSimple() {
    }

    @Test
    @Ignore
    override fun testSerializeNested() {
    }

    @Test
    @Ignore
    override fun testSerializeNull() {
    }
}
