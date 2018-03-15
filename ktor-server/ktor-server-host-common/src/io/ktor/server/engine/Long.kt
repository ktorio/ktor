package io.ktor.server.engine

private val longStrings = Array(1024) {
    it.toString()
}

internal fun Long.toStringFast() = if (this in 0..1023) longStrings[toInt()] else toString()

