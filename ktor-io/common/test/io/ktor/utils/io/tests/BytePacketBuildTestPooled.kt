package io.ktor.utils.io.tests

import io.ktor.utils.io.core.internal.*

class BytePacketBuildTestPooled : BytePacketBuildTest() {
    override val pool = VerifyingObjectPool(ChunkBuffer.Pool)
}
