package io.ktor.util

object Hash {
    fun combine(vararg objects: Any): Int = objects.toList().hashCode()
}