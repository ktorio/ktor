package io.ktor.http

import io.ktor.util.date.*

private const val HTTP_DATE_LENGTH = 29

/**
 * Convert valid http date [String] to [GMTDate]
 * according to: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Date
 * format: <day-name>{3}, <day>{2} <month>{3} <year>{4} <hour>{2}:<minute>{2}:<second>{2} GMT
 * [String] size should be equals 29
 *
 * Note that only date in GMT(UTC) is valid http date
 */
fun String.fromHttpToGmtDate(): GMTDate = with(trim()) {
    check(length == HTTP_DATE_LENGTH) {
        "Invalid date length. Expected $HTTP_DATE_LENGTH, actual $length. On string: $this"
    }

    check(endsWith("GMT")) { "Invalid timezone. Expected GMT. On string: $this"}

//    val weekDay = WeekDay.from(substring(0, 3))
    val day = substring(5, 7).toInt()
    val month = Month.from(substring(8, 11))
    val year = substring(12, 16).toInt()

    val hours = substring(17, 19).toInt()
    val minutes = substring(20, 22).toInt()
    val seconds = substring(23, 25).toInt()

    return GMTDate(
        seconds, minutes, hours,
        day, month, year
    )
}

/**
 * Convert [GMTDate] to valid http date [String]
 */
fun GMTDate.toHttpDate(): String = buildString {
    append("${dayOfWeek.value}, ")
    append("${dayOfMonth.padZero(2)} ")
    append("${month.value} ")
    append(year.padZero(4))
    append(" ${hours.padZero(2)}:${minutes.padZero(2)}:${seconds.padZero(2)} ")
    append("GMT")
}

private fun Int.padZero(length: Int): String = toString().padStart(length, '0')
