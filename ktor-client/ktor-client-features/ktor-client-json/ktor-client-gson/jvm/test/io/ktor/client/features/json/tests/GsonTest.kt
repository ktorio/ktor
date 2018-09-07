package io.ktor.client.features.json.tests

import io.ktor.client.features.json.*
import io.ktor.client.features.json.tests.*

class GsonTest: JsonTest() {
    override val serializerImpl = GsonSerializer()
}
