package io.ktor.http.cio.internals

class MutableRange(var start: Int, var end: Int) {
    override fun toString() = "MutableRange(start=$start, end=$end)"
}