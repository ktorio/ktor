package org.jetbrains.ktor.util

import java.util.concurrent.atomic.*

class Meeting(val parties: Int, val action: Meeting.() -> Unit) {
    private val current = AtomicInteger()

    init {
        require(parties > 0) { "parties should be positive (non zero)" }
    }

    val value: Int
        get() = current.get()

    fun reset() {
        require(value == parties) { "should be $parties (current = $value)" }
        require(current.addAndGet(-parties) == 0)
    }

    fun acknowledge(): Boolean {
        if (current.incrementAndGet() == parties) {
            action()
            return true
        }

        return false
    }

    override fun toString(): String {
        return "Meeting($current of $parties)"
    }


}
