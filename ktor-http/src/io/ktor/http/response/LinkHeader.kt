package io.ktor.http.response

import io.ktor.http.*

// RFC 5988
class LinkHeader(uri: String, params: List<HeaderValueParam>) : HeaderValueWithParameters("<$uri>", params) {
    constructor(uri: String, rel: String) : this(uri, listOf(HeaderValueParam(Parameters.Rel, rel)))
    constructor(uri: String, vararg rel: String) : this(uri, listOf(HeaderValueParam(Parameters.Rel, rel.joinToString(" "))))

    constructor(uri: String, rel: List<String>, type: ContentType)
    : this(uri, listOf(
            HeaderValueParam(Parameters.Rel, rel.joinToString(" ")),
            HeaderValueParam(Parameters.Type, type.toString())))

    val uri: String
        get() = content.removePrefix("<").removeSuffix(">")

    @Suppress("unused")
    object Parameters {
        val Rel = "rel"
        val Anchor = "anchor"
        val Rev = "Rev"
        val HrefLang = "hreflang"
        val Media = "media"
        val Title = "title"
        val Type = "type"
    }

    @Suppress("unused")
    object Rel {
        val Stylesheet = "stylesheet"

        val Prefetch = "prefetch"
        val DnsPrefetch = "dns-prefetch"
        val PreConnect = "preconnect"
        val PreLoad = "preload"
        val PreRender = "prerender"
        val Next = "next"
    }
}