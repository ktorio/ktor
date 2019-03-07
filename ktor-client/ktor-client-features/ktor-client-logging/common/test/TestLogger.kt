package io.ktor.client.features.logging

internal class TestLogger : Logger {
    private val state = StringBuilder()

    override fun log(message: String) {
        state.append("$message\n")
    }

    fun dump(): String = state.toString()
}
