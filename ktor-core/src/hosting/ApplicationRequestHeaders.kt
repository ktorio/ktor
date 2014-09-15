package ktor.application

fun ApplicationRequest.queryString(): String {
    val questionIndex = uri.indexOf('?')
    if (questionIndex == -1)
        return ""
    return uri.substring(questionIndex + 1)
}

fun ApplicationRequest.queryParameters(): Map<String, List<String>> {
    val query = queryString()
    if (query.isEmpty())
        return mapOf()
    val parameters = hashMapOf<String, MutableList<String>>()
    for (item in query.split("&")) {
        val pair = item.split("=")
        when(pair.size) {
            1 -> {
                parameters.getOrPut(pair[0], { arrayListOf() }).add("")
            }
            2 -> {
                parameters.getOrPut(pair[0], { arrayListOf() }).add(pair[1])
            }
        }
    }
    return parameters
}

fun ApplicationRequest.contentType(): ContentType {
    return header("Content-Type")?.let { ContentType.parse(it) } ?: ContentType.Any

}
fun ApplicationRequest.document(): String {
    return uri.drop(uri.lastIndexOf("/") + 1).takeWhile { it != '?' }
}

fun ApplicationRequest.path(): String {
    return uri.takeWhile { it != '?' }
}

fun ApplicationRequest.authorization(): String? {
    return header("Authorization")
}

fun ApplicationRequest.accept(): String? {
    return header("Accept")
}

fun ApplicationRequest.acceptEncoding(): String? {
    return header("Accept-Encoding")
}

fun ApplicationRequest.acceptLanguage(): String? {
    return header("Accept-Language")
}

fun ApplicationRequest.acceptCharset(): String? {
    return header("Accept-Charset")
}

fun ApplicationRequest.isChunked(): Boolean {
    return header("Transfer-Encoding")?.compareToIgnoreCase("chunked") == 0 ?: false
}

fun ApplicationRequest.userAgent(): String? {
    return header("User-Agent")
}

fun ApplicationRequest.cacheControl(): String? {
    return header("Cache-Control")
}

fun ApplicationRequest.host(): String? {
    return header("Host")?.takeWhile { it != ':' }
}

fun ApplicationRequest.port(): Int {
    return header("Host")?.dropWhile { it != ':' }?.drop(1)?.toInt() ?: 80
}
