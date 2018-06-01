package io.ktor.http

@Suppress("unused")
data class HttpStatusCode(val value: Int, val description: String) {
    override fun toString(): String = "$value $description"

    fun description(value: String) = copy(description = value)

    companion object {
        val Continue = HttpStatusCode(100, "Continue")
        val SwitchingProtocols = HttpStatusCode(101, "Switching Protocols")
        val Processing = HttpStatusCode(102, "Processing")

        val OK = HttpStatusCode(200, "OK")
        val Created = HttpStatusCode(201, "Created")
        val Accepted = HttpStatusCode(202, "Accepted")
        val NonAuthoritativeInformation = HttpStatusCode(203, "Non-Authoritative Information")
        val NoContent = HttpStatusCode(204, "No Content")
        val ResetContent = HttpStatusCode(205, "Reset Content")
        val PartialContent = HttpStatusCode(206, "Partial Content")

        val MultipleChoices = HttpStatusCode(300, "Multiple Choices")
        val MovedPermanently = HttpStatusCode(301, "Moved Permanently")
        val Found = HttpStatusCode(302, "Found")
        val SeeOther = HttpStatusCode(303, "See Other")
        val NotModified = HttpStatusCode(304, "Not Modified")
        val UseProxy = HttpStatusCode(305, "Use Proxy")
        val SwitchProxy = HttpStatusCode(306, "Switch Proxy")
        val TemporaryRedirect = HttpStatusCode(307, "Temporary Redirect")
        val PermanentRedirect = HttpStatusCode(308, "Permanent Redirect")

        val BadRequest = HttpStatusCode(400, "Bad Request")
        val Unauthorized = HttpStatusCode(401, "Unauthorized")
        val PaymentRequired = HttpStatusCode(402, "Payment Required")
        val Forbidden = HttpStatusCode(403, "Forbidden")
        val NotFound = HttpStatusCode(404, "Not Found")
        val MethodNotAllowed = HttpStatusCode(405, "Method Not Allowed")
        val NotAcceptable = HttpStatusCode(406, "Not Acceptable")
        val ProxyAuthenticationRequired = HttpStatusCode(407, "Proxy Authentication Required")
        val RequestTimeout = HttpStatusCode(408, "Request Timeout")
        val Conflict = HttpStatusCode(409, "Conflict")
        val Gone = HttpStatusCode(410, "Gone")
        val LengthRequired = HttpStatusCode(411, "Length Required")
        val PreconditionFailed = HttpStatusCode(412, "Precondition Failed")
        val PayloadTooLarge = HttpStatusCode(413, "Payload Too Large")
        val RequestURITooLong = HttpStatusCode(414, "Request-URI Too Long")

        val UnsupportedMediaType = HttpStatusCode(415, "Unsupported Media Type")
        val RequestedRangeNotSatisfiable = HttpStatusCode(416, "Requested Range Not Satisfiable")
        val ExceptionFailed = HttpStatusCode(417, "Exception Failed")
        val UpgradeRequired = HttpStatusCode(426, "Upgrade Required")
        val TooManyRequests = HttpStatusCode(429, "Too Many Requests")
        val RequestHeaderFieldTooLarge = HttpStatusCode(431, "Request Header Fields Too Large")

        val InternalServerError = HttpStatusCode(500, "Internal Server Error")
        val NotImplemented = HttpStatusCode(501, "Not Implemented")
        val BadGateway = HttpStatusCode(502, "Bad Gateway")
        val ServiceUnavailable = HttpStatusCode(503, "Service Unavailable")
        val GatewayTimeout = HttpStatusCode(504, "Gateway Timeout")
        val VersionNotSupported = HttpStatusCode(505, "HTTP Version Not Supported")
        val VariantAlsoNegotiates = HttpStatusCode(506, "Variant Also Negotiates")

        private val byValue by lazy {
            Array(1000) { idx ->
                allStatusCodes.firstOrNull { it.value == idx }
            }
        }

        fun fromValue(value: Int): HttpStatusCode {
            val knownStatus = if (value > 0 && value < 1000) byValue[value] else null
            return knownStatus ?: HttpStatusCode(value, "Unknown Status Code")
        }

        val allStatusCodes: List<HttpStatusCode> by lazy { io.ktor.http.allStatusCodes() }
    }
}

internal expect fun allStatusCodes(): List<HttpStatusCode>

fun HttpStatusCode.isSuccess(): Boolean = value in (200 until 300)
