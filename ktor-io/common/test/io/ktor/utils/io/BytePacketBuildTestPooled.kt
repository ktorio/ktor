package io.ktor.utils.io

class BytePacketBuildTestPooled : BytePacketBuildTest() {
    override val pool = VerifyingChunkBufferPool()
}
