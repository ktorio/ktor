package you.kube

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.util.*
import java.io.*

fun ApplicationCall.respondHtml(versions: List<Version>, visibility: CacheControlVisibility, block: HTML.() -> Unit): Nothing {
    respond(HtmlContent(versions, visibility, block))
}

fun ApplicationCall.respondDefaultHtml(versions: List<Version>, visibility: CacheControlVisibility, title: String = "You Kube", block: DIV.() -> Unit): Nothing {
    respondHtml(versions, visibility) {
        head {
            title { +title }
            styleLink(url(MainCss()))
        }
        body {
            h1 { +title }

            div("container") {
                block()
            }
        }
    }
}

class HtmlContent(override val versions: List<Version>, visibility: CacheControlVisibility, val builder: HTML.() -> Unit) : Resource, StreamContent {
    override val contentType: ContentType
        get() = ContentType.Text.Html.withParameter("charset", "utf-8")

    override val expires = null
    override val cacheControl = CacheControl.MaxAge(3600 * 24 * 7, mustRevalidate = true, visibility = visibility, proxyMaxAgeSeconds = null, proxyRevalidate = false)

    override val attributes = Attributes()
    override val contentLength = null

    override fun stream(out: OutputStream) {
        with(out.bufferedWriter()) {
            append("<!DOCTYPE html>\n")
            appendHTML().html(builder)
            flush()
        }
    }
}