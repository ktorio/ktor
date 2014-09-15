package ktor.application

enum class HttpStatusCode(val value: Int) {
    open val description: String
        get() = this.toString().replaceAll("([A-Z])", " $1")

    Continue : HttpStatusCode(100)
    SwitchingProtocols : HttpStatusCode(101)
    Processing : HttpStatusCode(102)

    OK : HttpStatusCode(200)
    Created : HttpStatusCode(201)
    Accepted : HttpStatusCode(202)
    NonAuthoritativeInformation : HttpStatusCode(203)
    NoContent : HttpStatusCode(204)
    ResetContent : HttpStatusCode(205)
    PartialContent : HttpStatusCode(206)

    // 3XX
    MultipleChoices : HttpStatusCode(300)
    MovedPermanently : HttpStatusCode(301)
    Found : HttpStatusCode(302)
    SeeOther : HttpStatusCode(303)
    NotModified : HttpStatusCode(304)
    UseProxy : HttpStatusCode(305)
    SwitchProxy : HttpStatusCode(306)
    TemporaryRedirect : HttpStatusCode(307)
    PermanentRedirect : HttpStatusCode(308)

    // 4XX
    BadRequest : HttpStatusCode(400)
    Unauthorized : HttpStatusCode(401)
    PaymentRequired : HttpStatusCode(402)
    Forbidden : HttpStatusCode(403)
    NotFound : HttpStatusCode(404)
    MethodNotAllowed : HttpStatusCode(405)
    NotAcceptable : HttpStatusCode(406)
    ProxyAuthenticationRequired : HttpStatusCode(407)
    RequestTimeout : HttpStatusCode(408)
    Conflict : HttpStatusCode(409)
    Gone : HttpStatusCode(410)
    LengthRequired : HttpStatusCode(411)
    PreconditionFailed : HttpStatusCode(412)
    RequestEntityTooLarge : HttpStatusCode(413)
    RequestURITooLarge : HttpStatusCode(414) {
        override val description: String = "Request-URI Too Large"
    }
    UnsupportedMediaType : HttpStatusCode(415)
    RequestedRageNotSatisfiable : HttpStatusCode(416)
    ExceptionFailed : HttpStatusCode(417)
    TooManyRequests : HttpStatusCode(429)
    RequestHeaderFieldTooLarge : HttpStatusCode(431)

    // 5XX
    InternalServerError : HttpStatusCode(500)
    NotImplemented : HttpStatusCode(501)
    BadGateway : HttpStatusCode(502)
    ServiceUnavailable : HttpStatusCode(503)
    GatewayTimeout : HttpStatusCode(504)
    VersionNotSupported : HttpStatusCode(505)
    VariantAlsoNegotiates : HttpStatusCode(506)
    InsufficientStorage : HttpStatusCode(507)
    BandwidthLimitExceeded : HttpStatusCode(509)
}

fun ApplicationResponse.status(code: HttpStatusCode) = status(code.value)