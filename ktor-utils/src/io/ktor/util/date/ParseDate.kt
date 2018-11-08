package io.ktor.util.date

fun parseRFC850(rfc850: String) : GMTDate{

    val regex = "([0-9]+)-([aA-zZ]+)-([0-9]+) ([0-9]+):([0-9]+):([0-9]+)".toRegex()
    val result = regex.find(rfc850)
    result?.let {
        val v = it.groupValues
        return GMTDate(v[6].toInt(),v[5].toInt(),v[4].toInt(), v[1].toInt(), Month.from(v[2]), yearCommonEra( v[3].toInt() ) )
    }
    return GMTDate.START
}

fun yearCommonEra(year: Int) = if (year<70) year + 2000 else year + 1900
