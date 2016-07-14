package org.jetbrains.ktor.util

import java.util.concurrent.atomic.*

class Meeting(val parties: Int, val action: Meeting.() -> Unit) {
    private val current = AtomicInteger()

    init {
        require(parties > 0) { "parties should be positive (non zero)" }
    }

    fun reset() {
        current.set(0)
    }

    fun acknowledge(): Boolean {
        if (current.incrementAndGet() == parties) {
            action()
            return true
        }

        return false
    }
}
