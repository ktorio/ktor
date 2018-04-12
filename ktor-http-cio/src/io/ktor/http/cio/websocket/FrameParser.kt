package io.ktor.http.cio.websocket

import java.nio.*
import java.util.concurrent.atomic.*

class FrameParser @Deprecated("Internal Api") constructor(){
    private val state = AtomicReference(State.HEADER0)

    var fin = false
        private set
    var mask = false
        private set

    private var opcode = 0
    private var lastOpcode = 0

    private var lengthLength = 0

    var length = 0L
        private set

    var maskKey: Int? = null
        private set

    val frameType: FrameType
        get() = FrameType[opcode] ?: throw IllegalStateException("Unsupported opcode ${Integer.toHexString(opcode)}")

    enum class State {
        HEADER0,
        LENGTH,
        MASK_KEY,
        BODY
    }

    val bodyReady: Boolean
        get() = state.get() == State.BODY

    fun bodyComplete() {
        if (!state.compareAndSet(State.BODY, State.HEADER0)) {
            throw IllegalStateException("It should be state BODY but it is ${state.get()}")
        }

        // lastOpcode should be never reset!
        opcode = 0
        length = 0L
        lengthLength = 0
        maskKey = null
    }

    tailrec fun frame(bb: ByteBuffer) {
        require(bb.order() == ByteOrder.BIG_ENDIAN) { "Buffer order should be BIG_ENDIAN but it is ${bb.order()}" }
        if (handleStep(bb)) frame(bb)
    }

    private fun handleStep(bb: ByteBuffer) = when (state.get()!!) {
        State.HEADER0 -> parseHeader1(bb)
        State.LENGTH -> parseLength(bb)
        State.MASK_KEY -> parseMaskKey(bb)
        State.BODY -> false
    }

    private fun parseHeader1(bb: ByteBuffer): Boolean {
        if (bb.remaining() >= 2) {
            val flagsAndOpcode = bb.get().toInt()
            val maskAndLength1 = bb.get().toInt()

            fin = flagsAndOpcode and 0x80 != 0
            opcode = (flagsAndOpcode and 0x0f).let { new -> if (new == 0) lastOpcode else new }
            if (!frameType.controlFrame) {
                lastOpcode = opcode
            }
            mask = maskAndLength1 and 0x80 != 0
            val length1 = maskAndLength1 and 0x7f

            lengthLength = when (length1) {
                126 -> 2
                127 -> 8
                else -> 0
            }

            length = if (lengthLength == 0) length1.toLong() else 0
            if (lengthLength > 0) {
                state.set(State.LENGTH)
            } else if (mask) {
                state.set(State.MASK_KEY)
            } else {
                state.set(State.BODY)
            }

            return true
        }

        return false
    }

    private fun parseLength(bb: ByteBuffer): Boolean {
        if (bb.remaining() >= lengthLength) {
            length = when (lengthLength) {
                2 -> bb.getShort().toLong() and 0xffff
                8 -> bb.getLong()
                else -> throw IllegalStateException()
            }

            if (mask) {
                state.set(State.MASK_KEY)
            } else {
                state.set(State.BODY)
            }

            return true
        }

        return false
    }

    private fun parseMaskKey(bb: ByteBuffer): Boolean {
        if (bb.remaining() >= 4) {
            maskKey = bb.getInt()

            state.set(State.BODY)
            return true
        }

        return false
    }
}