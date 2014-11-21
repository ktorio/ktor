package ktor.application

import java.util.*
import java.text.SimpleDateFormat

fun Long.toHttpDateString(): String {
    val calendar = Calendar.getInstance();
    calendar.setTimeInMillis(this)
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    return dateFormat.format(calendar.getTime());
}

fun String.fromHttpDateString(): Long {
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    return dateFormat.parse(this).getTime()
}