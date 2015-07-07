package org.jetbrains.ktor.http

object Headers {
    val knownCommaHeaders = listOf(
            "Accept",
            "Accept-Charset",
            "Accept-Encoding",
            "Accept-Language"
                                  )

    fun splitKnownHeaders(key: String, value: String): List<String> {
        if (key in knownCommaHeaders) {
            return value.split(",").map { it.trim() }
        }

        return listOf(value)
    }
}
