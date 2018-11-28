package io.ktor.client.features.json.tests

import io.ktor.client.features.json.*

class DefaultSerializerJsonTest: JsonTest() {
    // Force JsonFeature to use defaultSerializer()
    override val serializerImpl: JsonSerializer? = null
}
