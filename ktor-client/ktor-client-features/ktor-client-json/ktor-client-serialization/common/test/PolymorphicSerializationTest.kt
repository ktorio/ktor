/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.call.*
import io.ktor.client.features.json.serializer.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlin.reflect.*
import kotlin.test.*

@Serializable
sealed class TestSealed {
    @Serializable
    @SerialName("A")
    data class A(val valA: String): TestSealed()
    @Serializable
    @SerialName("B")
    data class B(val valB: String): TestSealed()
}

class PolymorphicSerializationTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testSerializationPolymorphic() {
        val simple = KotlinxSerializer()

        val data = listOf(TestSealed.A("a"), TestSealed.B("b"))

        val string = simple.writeContent(data, typeOf<List<TestSealed>>())

        assertEquals("""[{"type":"A","valA":"a"},{"type":"B","valB":"b"}]""", string)

        val makeInput = { text: String ->
            buildPacket { writeText(text) }
        }

        assertEquals(data, simple.read(typeInfo<List<TestSealed>>(), makeInput(string)))
    }
}
