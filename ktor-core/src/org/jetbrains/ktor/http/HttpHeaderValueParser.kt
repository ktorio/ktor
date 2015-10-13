package org.jetbrains.ktor.http

import java.util.*

public data class HeaderValueParam(val name: String, val value: String)
public data class HeaderValue(val value: String, val params: List<HeaderValueParam> = listOf()) {
    val quality: Float = params.firstOrNull { it.name == "q" }?.let { it.value.tryParseFloat() } ?: 1.0f
}

private fun String.tryParseFloat(): Float {
    try {
        return toFloat()
    } catch(e: NumberFormatException) {
        return 0f
    }
}

public fun parseAndSortHeader(header: String?): List<HeaderValue> = parseHeaderValue(header).sortedByDescending { it.quality }
public fun parseAndSortContentTypeHeader(header: String?): List<HeaderValue> = parseHeaderValue(header).sortedWith(
        compareByDescending<HeaderValue> { it.quality }.thenBy {
            val contentType = ContentType.parse(it.value)
            var asterisks = 0
            if (contentType.contentType == "*")
                asterisks += 2
            if (contentType.contentSubtype == "*")
                asterisks++
            asterisks
        }.thenByDescending {
            it.params.size
        })

public fun parseHeaderValue(text: String?): List<HeaderValue> {
    if (text == null)
        return emptyList()

    var pos = 0
    val items = lazy { arrayListOf<HeaderValue>() }
    while (pos <= text.lastIndex) {
        pos = parseHeaderValueItem(text, pos, items)
    }
    return items.valueOrEmpty()
}

private fun <T> Lazy<List<T>>.valueOrEmpty(): List<T> = if (isInitialized()) value else emptyList()
private fun String.subtrim(start: Int, end: Int): String {
    return substring(start, end).trim()
}

private fun parseHeaderValueItem(text: String, start: Int, items: Lazy<ArrayList<HeaderValue>>): Int {
    var pos = start
    val parameters = lazy { arrayListOf<HeaderValueParam>() }
    var valueEnd: Int? = null
    while (pos <= text.lastIndex) {
        when (text[pos]) {
            ',' -> {
                items.value.add(HeaderValue(text.subtrim(start, valueEnd ?: pos), parameters.valueOrEmpty()))
                return pos + 1
            }
            ';' -> {
                if (valueEnd == null) valueEnd = pos
                pos = parseHeaderValueParameter(text, pos + 1, parameters)
            }
            else -> pos++
        }
    }
    items.value.add(HeaderValue(text.subtrim(start, valueEnd ?: pos), parameters.valueOrEmpty()))
    return pos
}

private fun parseHeaderValueParameter(text: String, start: Int, parameters: Lazy<ArrayList<HeaderValueParam>>): Int {
    fun Lazy<ArrayList<HeaderValueParam>>.addParam(text: String, start: Int, end: Int, value: String) {
        val name = text.subtrim(start, end)
        if (name.isEmpty())
            return
        this.value.add(HeaderValueParam(name, value))
    }

    var pos = start
    while (pos <= text.lastIndex) {
        when (text[pos]) {
            '=' -> {
                val (paramEnd, paramValue) = parseHeaderValueParameterValue(text, pos + 1)
                parameters.addParam(text, start, pos, paramValue)
                return paramEnd
            }
            ';', ',' -> {
                parameters.addParam(text, start, pos, "")
                return pos
            }
            else -> pos++
        }
    }
    parameters.addParam(text, start, pos, "")
    return pos
}


private fun parseHeaderValueParameterValue(value: String, start: Int): Pair<Int, String> {
    var pos = start
    while (pos <= value.lastIndex) {
        when (value[pos]) {
            '"' -> return parseHeaderValueParameterValueQuoted(value, pos + 1)
            ';', ',' -> return pos to value.subtrim(start, pos)
            else -> pos++
        }
    }
    return pos to value.subtrim(start, pos)
}

private fun parseHeaderValueParameterValueQuoted(value: String, start: Int): Pair<Int, String> {
    var pos = start
    val sb = StringBuilder()
    while (pos <= value.lastIndex) {
        val c = value[pos]
        when (c) {
            '"' -> return pos + 1 to sb.toString()
            '\\' -> {
                if (pos < value.lastIndex - 2) {
                    sb.append(value[pos + 1])
                    pos += 2
                } // quoted value
                else {
                    sb.append(c)
                    pos++ // broken value, escape at the end
                }
            }
            else -> {
                sb.append(c)
                pos++
            }
        }
    }
    return pos to sb.toString()
}
