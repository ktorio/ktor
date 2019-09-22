package io.ktor.utils.io.internal

import java.nio.ByteBuffer

// this is MAGICAL constant that is tied to the code ByteBufferChannel (that is how much it needs extra)
internal const val RESERVED_SIZE = 8

internal val EmptyByteBuffer: ByteBuffer = ByteBuffer.allocate(0)
internal val EmptyCapacity = RingBufferCapacity(0)

internal sealed class ReadWriteBufferState(
    @JvmField val backingBuffer: ByteBuffer,
    @JvmField val capacity: RingBufferCapacity
) {
    open val idle: Boolean get() = false
    open val readBuffer: ByteBuffer get() = error("read buffer is not available in state $this")
    open val writeBuffer: ByteBuffer get() = error("write buffer is not available in state $this")

    internal open fun startReading(): ReadWriteBufferState = error("Reading is not available in state $this")
    internal open fun startWriting(): ReadWriteBufferState = error("Writing is not available in state $this")
    internal open fun stopReading(): ReadWriteBufferState = error("Unable to stop reading in state $this")
    internal open fun stopWriting(): ReadWriteBufferState = error("Unable to stop writing in state $this")

    object IdleEmpty : ReadWriteBufferState(EmptyByteBuffer, EmptyCapacity) {
        override val idle: Boolean get() = true
        override fun toString() = "IDLE(empty)"
    }

    class Initial(
        backingBuffer: ByteBuffer,
        reservedSize: Int = RESERVED_SIZE
    ) : ReadWriteBufferState(backingBuffer, RingBufferCapacity(backingBuffer.capacity() - reservedSize)) {
        init {
            require(backingBuffer.position() == 0)
            require(backingBuffer.limit() == backingBuffer.capacity())
        }
        override val writeBuffer: ByteBuffer = backingBuffer.duplicate() // defensive copy of buffer's state
        override val readBuffer: ByteBuffer = backingBuffer.duplicate() // must have a separate buffer state here
        // all other possible states
        internal val idleState = IdleNonEmpty(this)
        internal val readingState = Reading(this)
        internal val writingState = Writing(this)
        internal val readingWritingState = ReadingWriting(this)
        // state transitions
        override fun startReading() = readingState
        override fun startWriting() = writingState
        override val idle: Boolean get() = error("Not available for initial state")
        override fun toString() = "Initial"
    }

    class IdleNonEmpty internal constructor(
        val initial: Initial // public here, so can release initial state when idle
    ) : ReadWriteBufferState(initial.backingBuffer, initial.capacity) {
        override fun startReading() = initial.readingState
        override fun startWriting() = initial.writingState
        override val idle: Boolean get() = true
        override fun toString() = "IDLE(with buffer)"
    }

    class Reading internal constructor(
        private val initial: Initial
    ) : ReadWriteBufferState(initial.backingBuffer, initial.capacity) {
        override val readBuffer: ByteBuffer get() = initial.readBuffer
        override fun startWriting() = initial.readingWritingState
        override fun stopReading() = initial.idleState
        override fun toString() = "Reading"
    }

    class Writing internal constructor(
        private val initial: Initial
    ) : ReadWriteBufferState(initial.backingBuffer, initial.capacity) {
        override val writeBuffer: ByteBuffer get() = initial.writeBuffer
        override fun startReading() = initial.readingWritingState
        override fun stopWriting() = initial.idleState
        override fun toString() = "Writing"
    }

    class ReadingWriting internal constructor(
        private val initial: Initial
    ) : ReadWriteBufferState(initial.backingBuffer, initial.capacity) {
        override val readBuffer: ByteBuffer get() = initial.readBuffer
        override val writeBuffer: ByteBuffer get() = initial.writeBuffer
        override fun stopReading() = initial.writingState
        override fun stopWriting() = initial.readingState
        override fun toString() = "Reading+Writing"
    }

    object Terminated : ReadWriteBufferState(EmptyByteBuffer, EmptyCapacity) {
        override fun toString() = "Terminated"
    }
}
