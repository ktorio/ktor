package ktor.application

enum class HttpStatusCode(val value: Int) {
    Continue(100),
    SwitchingProtocols(101),
    Processing(102),

    OK(200),
    Created(201),
    Accepted(202),
    NonAuthoritativeInformation(203),
    NoContent(204),
    ResetContent(205),
    PartialContent(206),

    // 3XX
    MultipleChoices(300),
    MovedPermanently(301),
    Found(302),
    SeeOther(303),
    NotModified(304),
    UseProxy(305),
    SwitchProxy(306),
    TemporaryRedirect(307),
    PermanentRedirect(308),

    // 4XX
    BadRequest(400),
    Unauthorized(401),
    PaymentRequired(402),
    Forbidden(403),
    NotFound(404),
    MethodNotAllowed(405),
    NotAcceptable(406),
    ProxyAuthenticationRequired(407),
    RequestTimeout(408),
    Conflict(409),
    Gone(410),
    LengthRequired(411),
    PreconditionFailed(412),
    RequestEntityTooLarge(413),
    RequestURITooLarge(414) {
        override val description: String = "Request-URI Too Large"
    },
    UnsupportedMediaType(415),
    RequestedRageNotSatisfiable(416),
    ExceptionFailed(417),
    TooManyRequests(429),
    RequestHeaderFieldTooLarge(431),

    // 5XX
    InternalServerError(500),
    NotImplemented(501),
    BadGateway(502),
    ServiceUnavailable(503),
    GatewayTimeout(504),
    VersionNotSupported(505),
    VariantAlsoNegotiates(506),
    InsufficientStorage(507),
    BandwidthLimitExceeded(509);

    open val description: String
        get() = this.toString().replace("([A-Z])".toRegex(), " $1")
}

fun ApplicationResponse.status(code: HttpStatusCode) = status(code.value)