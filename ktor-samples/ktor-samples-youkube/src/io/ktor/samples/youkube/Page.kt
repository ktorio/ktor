package io.ktor.samples.youkube

import kotlinx.html.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.sessions.*

suspend fun ApplicationCall.respondDefaultHtml(versions: List<Version>, visibility: CacheControl.Visibility, title: String = "You Kube", block: DIV.() -> Unit) {
    val cacheControl = CacheControl.MaxAge(3600 * 24 * 7, mustRevalidate = true, visibility = visibility, proxyMaxAgeSeconds = null, proxyRevalidate = false)
    respondHtml(HttpStatusCode.OK, versions, cacheControl) {
        val session = sessions.get<YouKubeSession>()
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
                                        a(classes = "pure-button", href = locations.href(Login())) { +"Login" }
                                    } else {
                                        a(classes = "pure-button", href = locations.href(Upload())) { +"Upload" }
                                    }
                                }
                                li("nav-item") {
                                    a(classes = "pure-button", href = locations.href(Index())) { +"Watch" }
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


