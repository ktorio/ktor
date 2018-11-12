package io.ktor.util.date

fun parseDateRFC850(rfc850: String) = try {
    val subDate = rfc850.subSequence(rfc850.indexOf(',') + 2, rfc850.length)
    val parts = subDate.split(' ')
    val date = parts[0].split('-')
    val time = parts[1].split(':')

    GMTDate(
        time[2].toInt(),
        time[1].toInt(),
        time[0].toInt(),
        date[0].toInt(),
        Month.from(date[1]),
        yearCommonEra(date[2].toInt())
    )
} catch (cause: Throwable) {
    throw DateParserException(rfc850, cause)
}

internal fun yearCommonEra(year: Int) = if (year < 70) year + 2000 else year + 1900

/**
 * Thrown when failed to parse Date
 */
class DateParserException(dateString: String, cause: Throwable) : IllegalStateException(
    "Fail to parse date: $dateString", cause
)
