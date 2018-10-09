package io.ktor.http

/**
 * Represents a `Link` header value as per RFC 5988
 */
class LinkHeader(uri: String, params: List<HeaderValueParam>) : HeaderValueWithParameters("<$uri>", params) {
    @Suppress("unused")
    constructor(uri: String, rel: String) : this(uri, listOf(HeaderValueParam(Parameters.Rel, rel)))

    constructor(uri: String, vararg rel: String) : this(uri, listOf(HeaderValueParam(Parameters.Rel, rel.joinToString(" "))))

    @Suppress("unused")
    constructor(uri: String, rel: List<String>, type: ContentType)
    : this(uri, listOf(
            HeaderValueParam(Parameters.Rel, rel.joinToString(" ")),
            HeaderValueParam(Parameters.Type, type.toString())))

    /**
     * Link URI part
     */
    val uri: String
        get() = content.removePrefix("<").removeSuffix(">")

    /**
     * Known Link header parameters
     */
    @Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType")
    object Parameters {
        const val Rel = "rel"
        const val Anchor = "anchor"
        const val Rev = "Rev"
        const val HrefLang = "hreflang"
        const val Media = "media"
        const val Title = "title"
        const val Type = "type"
    }

    /**
     * Known rel parameter values
     */
    @Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType")
    object Rel {
        const val Stylesheet = "stylesheet"

        const val Prefetch = "prefetch"
        const val DnsPrefetch = "dns-prefetch"
        const val PreConnect = "preconnect"
        const val PreLoad = "preload"
        const val PreRender = "prerender"
        const val Next = "next"
    }
}
