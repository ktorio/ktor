package io.ktor.utils.io

internal expect class Condition(predicate: () -> Boolean) {
    fun check(): Boolean
    suspend fun await()
    suspend fun await(block: () -> Unit)
    fun signal()
}
