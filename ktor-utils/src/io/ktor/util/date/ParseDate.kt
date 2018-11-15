package io.ktor.util.date

fun parseDateRFC850(rfc850: String) = try {
    GMTDate(
        rfc850.substring(21,23).toInt(),
        rfc850.substring(18,20).toInt(),
        rfc850.substring(15,17).toInt(),
        rfc850.substring(5,7).toInt(),
        Month.from(rfc850.substring(8,11)),
        yearCommonEra(rfc850.substring(12,14).toInt())
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
