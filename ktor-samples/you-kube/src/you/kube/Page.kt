package you.kube

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.sessions.*
import java.io.*

fun ApplicationCall.respondHtml(versions: List<Version>, visibility: CacheControlVisibility, block: HTML.() -> Unit): Nothing {
    respond(HtmlContent(versions, visibility, block))
}

fun ApplicationCall.respondDefaultHtml(versions: List<Version>, visibility: CacheControlVisibility, title: String = "You Kube", block: DIV.() -> Unit): Nothing {
    respondHtml(versions, visibility) {
        val session = sessionOrNull<Session>()
        head {
            title { +title }
            styleLink("http://yui.yahooapis.com/pure/0.6.0/pure-min.css")
            styleLink("http://yui.yahooapis.com/pure/0.6.0/grids-responsive-min.css")
            styleLink(url(MainCss()))
        }
        body {
            div("pure-g") {
                div("sidebar pure-u-1 pure-u-md-1-4") {
                    div("header") {
                        div("brand-title") { +title }
                        div("brand-tagline") {
                            if (session != null) {
                                +session.userId
                            }
                        }

                        nav("nav") {
                            ul("nav-list") {
                                li("nav-item") {
                                    if (session == null) {
                                        a(classes = "pure-button", href = application.feature(Locations).href(Login())) { +"Login" }
                                    } else {
                                        a(classes = "pure-button", href = application.feature(Locations).href(Upload())) { +"Upload" }
                                    }
                                }
                                li("nav-item") {
                                    a(classes = "pure-button", href = application.feature(Locations).href(Index())) { +"Watch" }
                                }
                            }
                        }
                    }
                }

                div("content pure-u-1 pure-u-md-3-4") {
                    block()
                }
            }
        }
    }
}

class HtmlContent(override val versions: List<Version>, visibility: CacheControlVisibility, val builder: HTML.() -> Unit) : Resource, StreamConsumer() {
    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override val expires = null
    override val cacheControl = CacheControl.MaxAge(3600 * 24 * 7, mustRevalidate = true, visibility = visibility, proxyMaxAgeSeconds = null, proxyRevalidate = false)

    override val contentLength = null

    override val headers by lazy { super.headers }

    override fun stream(out: OutputStream) {
        with(out.bufferedWriter()) {
            append("<!DOCTYPE html>\n")
            appendHTML().html(builder)
            flush()
        }
    }
}