package io.ktor.utils.io

import io.ktor.utils.io.core.*

class ByteBufferChannelScenarioSequentialTest : ByteBufferChannelScenarioTest() {

    override fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return ByteChannelSequentialJVM(IoBuffer.Empty, autoFlush)
    }
}
