package io.ktor.client.features.json.tests

class DefaultSerializerJsonTest: JsonTest() {
    // Force JsonFeature to use defaultSerializer()
    override val serializerImpl = null
}
