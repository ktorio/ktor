package io.ktor.http

/**
 * Represents an HTTP status code and description.
 * @param value is a numeric code.
 * @param description is free form description of a status.
 */
@Suppress("unused")
data class HttpStatusCode(val value: Int, val description: String) {
    override fun toString(): String = "$value $description"

    /**
     * Returns a copy of `this` code with a description changed to [value].
     */
    fun description(value: String): HttpStatusCode = copy(description = value)

    @Suppress("KDocMissingDocumentation", "PublicApiImplicitType")
    companion object {
        // =============================================================================================================
        // Disclaimer
        // Adding a new status code here please remember [allStatusCodes] as well
        //

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
        val MultiStatus = HttpStatusCode(207, "Multi-Status")

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
        val ExpectationFailed = HttpStatusCode(417, "Expectation Failed")
        val UnprocessableEntity = HttpStatusCode(422, "Unprocessable Entity")
        val Locked = HttpStatusCode(423, "Locked")
        val FailedDependency = HttpStatusCode(424, "Failed Dependency")
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
        val InsufficientStorage = HttpStatusCode(507, "Insufficient Storage")

        /**
         * All known status codes
         */
        val allStatusCodes: List<HttpStatusCode> = io.ktor.http.allStatusCodes()

        private val byValue: Array<HttpStatusCode?> = Array(1000) { idx ->
            allStatusCodes.firstOrNull { it.value == idx }
        }

        /**
         * Creates an instance of [HttpStatusCode] with the given numeric value.
         */
        fun fromValue(value: Int): HttpStatusCode {
            val knownStatus = if (value in 1 until 1000) byValue[value] else null
            return knownStatus ?: HttpStatusCode(value, "Unknown Status Code")
        }
    }
}

@Suppress("UNUSED", "KDocMissingDocumentation")
@Deprecated(
    "Use ExpectationFailed instead",
    ReplaceWith("ExpectationFailed", "io.ktor.http.HttpStatusCode.Companion.ExpectationFailed")
)
inline val HttpStatusCode.Companion.ExceptionFailed: HttpStatusCode
    get() = ExpectationFailed

internal fun allStatusCodes(): List<HttpStatusCode> = listOf(
    HttpStatusCode.Continue,
    HttpStatusCode.SwitchingProtocols,
    HttpStatusCode.Processing,
    HttpStatusCode.OK,
    HttpStatusCode.Created,
    HttpStatusCode.Accepted,
    HttpStatusCode.NonAuthoritativeInformation,
    HttpStatusCode.NoContent,
    HttpStatusCode.ResetContent,
    HttpStatusCode.PartialContent,
    HttpStatusCode.MultiStatus,
    HttpStatusCode.MultipleChoices,
    HttpStatusCode.MovedPermanently,
    HttpStatusCode.Found,
    HttpStatusCode.SeeOther,
    HttpStatusCode.NotModified,
    HttpStatusCode.UseProxy,
    HttpStatusCode.SwitchProxy,
    HttpStatusCode.TemporaryRedirect,
    HttpStatusCode.PermanentRedirect,
    HttpStatusCode.BadRequest,
    HttpStatusCode.Unauthorized,
    HttpStatusCode.PaymentRequired,
    HttpStatusCode.Forbidden,
    HttpStatusCode.NotFound,
    HttpStatusCode.MethodNotAllowed,
    HttpStatusCode.NotAcceptable,
    HttpStatusCode.ProxyAuthenticationRequired,
    HttpStatusCode.RequestTimeout,
    HttpStatusCode.Conflict,
    HttpStatusCode.Gone,
    HttpStatusCode.LengthRequired,
    HttpStatusCode.PreconditionFailed,
    HttpStatusCode.PayloadTooLarge,
    HttpStatusCode.RequestURITooLong,
    HttpStatusCode.UnsupportedMediaType,
    HttpStatusCode.RequestedRangeNotSatisfiable,
    HttpStatusCode.ExpectationFailed,
    HttpStatusCode.UnprocessableEntity,
    HttpStatusCode.Locked,
    HttpStatusCode.FailedDependency,
    HttpStatusCode.UpgradeRequired,
    HttpStatusCode.TooManyRequests,
    HttpStatusCode.RequestHeaderFieldTooLarge,
    HttpStatusCode.InternalServerError,
    HttpStatusCode.NotImplemented,
    HttpStatusCode.BadGateway,
    HttpStatusCode.ServiceUnavailable,
    HttpStatusCode.GatewayTimeout,
    HttpStatusCode.VersionNotSupported,
    HttpStatusCode.VariantAlsoNegotiates,
    HttpStatusCode.InsufficientStorage
)

/**
 * Checks if a given status code is a success code according to HTTP standards.
 *
 * Codes from 200 to 299 are considered to be successful.
 */
fun HttpStatusCode.isSuccess(): Boolean = value in (200 until 300)
