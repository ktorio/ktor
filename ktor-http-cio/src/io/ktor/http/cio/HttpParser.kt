package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.*

class ParserException(message: String) : Exception(message)

private const val HTTP_LINE_LIMIT = 8192

suspend fun parseRequest(input: ByteReadChannel): Request? {
    val builder = CharBufferBuilder()
    val range = MutableRange(0, 0)

    try {
        while (true) {
            if (!input.readUTF8LineTo(builder, HTTP_LINE_LIMIT)) return null
            range.end = builder.length
            if (range.start == range.end) continue

            val method = parseHttpMethod(builder, range)
            val uri = parseUri(builder, range)
            val version = parseVersion(builder, range)
            skipSpaces(builder, range)

            if (range.start != range.end) throw ParserException("Extra characters in request line: ${builder.substring(range.start, range.end)}")
            if (uri.isEmpty()) throw ParserException("URI is not specified")
            if (version.isEmpty()) throw ParserException("HTTP version is not specified")

            val headers = parseHeaders(input, builder, range) ?: return null

            return Request(method, uri, version, headers, builder)
        }
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

suspend fun parseResponse(input: ByteReadChannel): Response? {
    val builder = CharBufferBuilder()
    val range = MutableRange(0, 0)

    try {
        if (!input.readUTF8LineTo(builder, HTTP_LINE_LIMIT)) return null
        range.end = builder.length

        val version = parseVersion(builder, range)
        val statusCode = parseStatusCode(builder, range)
        skipSpaces(builder, range)
        val statusText = builder.subSequence(range.start, range.end)
        range.start = range.end

        val headers = parseHeaders(input, builder, range) ?: return null

        return Response(version, statusCode, statusText, headers, builder)
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

internal suspend fun parseHeaders(input: ByteReadChannel, builder: CharBufferBuilder, range: MutableRange = MutableRange(0, 0)): HttpHeadersMap? {
    val headers = HttpHeadersMap(builder)

    try {
        while (true) {
            if (!input.readUTF8LineTo(builder, HTTP_LINE_LIMIT)) {
                headers.release()
                return null
            }

            range.end = builder.length

            skipSpaces(builder, range)

            range.end = builder.length
            if (range.start == range.end) break

            val nameStart = range.start
            val nameEnd = findColonOrSpace(builder, range)
            val nameHash = builder.hashCodeLowerCase(nameStart, nameEnd)
            range.start = nameEnd

            skipSpacesAndColon(builder, range)
            if (range.start == range.end) throw ParserException("No HTTP header value provided for name ${builder.substring(nameStart, nameEnd)}: \n$builder")

            // TODO check for trailing spaces in HTTP spec

            val valueStart = range.start
            val valueEnd = range.end
            val valueHash = builder.hashCodeLowerCase(valueStart, valueEnd)
            range.start = valueEnd

            headers.put(nameHash, valueHash, nameStart, nameEnd, valueStart, valueEnd)
        }

        return headers
    } catch (t: Throwable) {
        headers.release()
        throw t
    }
}


private fun parseHttpMethod(text: CharSequence, range: MutableRange): HttpMethod {
    skipSpaces(text, range)
    val exact = DefaultHttpMethods.search(text, range.start, range.end) { ch, _ -> ch == ' ' }.singleOrNull()
    if (exact != null) {
        range.start += exact.value.length
        return exact
    }

    return parseHttpMethodFull(text, range)
}

private fun parseHttpMethodFull(text: CharSequence, range: MutableRange): HttpMethod {
    return HttpMethod(nextToken(text, range).toString())
}

private fun parseUri(text: CharSequence, range: MutableRange): CharSequence {
    skipSpaces(text, range)
    val start = range.start
    val spaceOrEnd = findSpaceOrEnd(text, range)
    val length = spaceOrEnd - start

    if (length <= 0) return ""
    if (length == 1 && text[start] == '/') {
        range.start = spaceOrEnd
        return "/"
    }

    val s = text.subSequence(start, spaceOrEnd)
    range.start = spaceOrEnd
    return s
}


private val versions = AsciiCharTree.build(listOf("HTTP/1.0", "HTTP/1.1"))
private fun parseVersion(text: CharSequence, range: MutableRange): CharSequence {
    skipSpaces(text, range)
    val exact = versions.search(text, range.start, range.end) { ch, _ -> ch == ' ' }.singleOrNull()
    if (exact != null) {
        range.start += exact.length
        return exact
    }

    return nextToken(text, range)
}

private fun parseStatusCode(text: CharSequence, range: MutableRange): Int {
    skipSpaces(text, range)
    var status = 0
    var newStart = range.end

    for (idx in range.start until range.end) {
        val ch = text[idx]
        if (ch == ' ') {
            newStart = idx
            break
        } else if (ch in '0'..'9') {
            status = status * 10 + (ch - '0')
        } else {
            throw NumberFormatException("Illegal digit $ch in status code ${text.substring(range.start, findSpaceOrEnd(text, range))}")
        }
    }

    range.start = newStart
    return status
}
