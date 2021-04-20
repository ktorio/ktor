package io.ktor.utils.io

import io.ktor.utils.io.core.internal.*

class StringsSequentialTest : StringsTest() {
    override fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return ByteChannelSequentialJVM(ChunkBuffer.Empty, autoFlush)
    }
}
