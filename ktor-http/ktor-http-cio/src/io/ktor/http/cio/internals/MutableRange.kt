package io.ktor.http.cio.internals

internal class MutableRange(var start: Int, var end: Int) {
    override fun toString() = "MutableRange(start=$start, end=$end)"
}