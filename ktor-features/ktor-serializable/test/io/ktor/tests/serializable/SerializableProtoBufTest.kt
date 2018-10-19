package io.ktor.tests.serializable

import io.ktor.http.*
import io.ktor.serializable.*
import io.ktor.server.testing.*
import kotlinx.serialization.protobuf.*

class SerializableProtoBufTest : SerializableTest() {
    override val contentType = ContentType.Application.ProtoBuf
    override val contentConverter = SerializableProtoBufConverter()

    override fun parseResponse(response: TestApplicationResponse): MyEntity {
        return ProtoBuf.load(response.byteContent!!)
    }

    override fun createRequest(entity: MyEntity, request: TestApplicationRequest) {
        request.setBody(ProtoBuf.dump(entity))
    }
}
