package io.ktor.client.features.json.tests

import io.ktor.client.features.json.serializer.*
import kotlinx.serialization.*

class KotlinxJsonTest: JsonTest() {
    override val serializerImpl = KotlinxSerializer().apply {
        // TODO Improve support for parameterized serializables
        setMapper(Response::class, Response.serializer(User.serializer()) as KSerializer<Response<*>>)
        register<Widget>()
        register<User>()
    }
}
