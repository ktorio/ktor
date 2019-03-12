package io.ktor.util

@KtorExperimentalAPI
object Hash {
    fun combine(vararg objects: Any): Int = objects.toList().hashCode()
}
