package io.ktor.util

/**
 * Escapes the characters in a String using HTML entities
 */
fun String.escapeHTML(): String {
    val text = this@escapeHTML
    if (text.isEmpty()) return text

    return buildString(length) {
        for (idx in 0 until text.length) {
            val ch = text[idx]
            when (ch) {
                '\'' -> append("&apos;")
                '\"' -> append("&quot")
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }
}

/**
 * Splits the given string into two parts before and after separator.
 *
 * Useful together with destructuring declarations
 */
inline fun String.chomp(separator: String, onMissingDelimiter: () -> Pair<String, String>): Pair<String, String> {
    val idx = indexOf(separator)
    return when (idx) {
        -1 -> onMissingDelimiter()
        else -> substring(0, idx) to substring(idx + 1)
    }
}

