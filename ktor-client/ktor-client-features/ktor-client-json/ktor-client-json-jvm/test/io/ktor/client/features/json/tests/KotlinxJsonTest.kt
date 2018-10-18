package io.ktor.client.features.json.tests

import io.ktor.client.features.json.serializer.*
import kotlinx.serialization.internal.*

class KotlinxJsonTest : JsonTest() {
    override val serializerImpl = KotlinxSerializer().apply {
        register(Response.serializer(ArrayListSerializer(User.serializer())))
        register<Widget>()
        register<User>()
    }
}
