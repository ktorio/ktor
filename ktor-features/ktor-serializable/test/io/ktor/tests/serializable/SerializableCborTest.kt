package io.ktor.tests.serializable

import io.ktor.http.*
import io.ktor.serializable.*
import io.ktor.server.testing.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*

class SerializableCborTest : SerializableTest() {
    override val contentType = ContentType.Application.Cbor
    override val contentConverter = SerializableCborConverter()

    override fun parseResponse(response: TestApplicationResponse): MyEntity {
        return CBOR.load(response.byteContent!!)
    }

    override fun createRequest(entity: MyEntity, request: TestApplicationRequest) {
        request.setBody(CBOR.dump(entity))
    }
}
