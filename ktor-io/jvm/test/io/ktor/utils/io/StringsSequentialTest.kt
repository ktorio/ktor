package io.ktor.utils.io

import io.ktor.utils.io.core.*

class StringsSequentialTest : StringsTest() {
    override fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return ByteChannelSequentialJVM(IoBuffer.Empty, autoFlush)
    }
}
