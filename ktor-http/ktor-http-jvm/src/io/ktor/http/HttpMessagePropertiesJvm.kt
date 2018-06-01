package io.ktor.http

import java.text.*
import java.util.*

private val HTTP_DATE_FORMAT: SimpleDateFormat
    get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

private fun parseHttpDate(date: String): Date = HTTP_DATE_FORMAT.parse(date)

private fun formatHttpDate(date: Date): String = HTTP_DATE_FORMAT.format(date)

fun HttpMessageBuilder.ifModifiedSince(date: Date) = headers.set(HttpHeaders.IfModifiedSince, formatHttpDate(date))
fun HttpMessageBuilder.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { parseHttpDate(it) }
fun HttpMessageBuilder.expires(): Date? = headers[HttpHeaders.Expires]?.let { parseHttpDate(it) }
fun HttpMessage.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { parseHttpDate(it) }
fun HttpMessage.expires(): Date? = headers[HttpHeaders.Expires]?.let { parseHttpDate(it) }
