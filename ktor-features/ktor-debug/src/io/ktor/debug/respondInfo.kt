package io.ktor.debug

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import kotlinx.html.*

/**
 * Responds with a HTML describing all the request information available (and optionally server information like environment variables).
 * This can be useful when debugging requests or when using reverse-proxies where the headers of the client might be manipulated in the process.
 *
 * Setting [includeServerInfo] to true, will include information like server environment variables.
 * The environment variables might contain sensitive information, so be careful and protect the request when setting it.
 */
suspend fun ApplicationCall.respondInfo(includeServerInfo: Boolean = false) {
    fun TABLE.row(key: String, value: Any?) {
        tr {
            th { +key }
            td { +value.toString() }
        }
    }

    respondHtml {
        body {
            style {
                +"""
                    table {
                        font: 1em Arial;
                        border: 1px solid black;
                        width: 100%;
                    }
                    th {
                        background-color: #ccc;
                        width: 200px;
                    }
                    td {
                        background-color: #eee;
                    }
                    th, td {
                        text-align: left;
                        padding: 0.5em 1em;
                    }
                """.trimIndent()
            }
            h1 {
                +"Ktor info"
            }
            h2 {
                +"Info"
            }
            table {
                row("request.httpVersion", request.httpVersion)
                row("request.httpMethod", request.httpMethod)
                row("request.uri", request.uri)
                row("request.path()", request.path())
                row("request.host()", request.host())
                row("request.document()", request.document())
                row("request.location()", request.location())
                row("request.queryParameters", request.queryParameters.formUrlEncode())

                row("request.userAgent()", request.userAgent())

                row("request.accept()", request.accept())
                row("request.acceptCharset()", request.acceptCharset())
                row("request.acceptCharsetItems()", request.acceptCharsetItems())
                row("request.acceptEncoding()", request.acceptEncoding())
                row("request.acceptEncodingItems()", request.acceptEncodingItems())
                row("request.acceptLanguage()", request.acceptLanguage())
                row("request.acceptLanguageItems()", request.acceptLanguageItems())

                row("request.authorization()", request.authorization())
                row("request.cacheControl()", request.cacheControl())

                row("request.contentType()", request.contentType())
                row("request.contentCharset()", request.contentCharset())
                row("request.isChunked()", request.isChunked())
                row("request.isMultipart()", request.isMultipart())

                row("request.ranges()", request.ranges())
            }

            for ((name, value) in listOf(
                "request.local" to request.local,
                "request.origin" to request.origin
            )) {
                h2 {
                    +name
                }
                table {
                    row("$name.version", value.version)
                    row("$name.method", value.method)
                    row("$name.scheme", value.scheme)
                    row("$name.host", value.host)
                    row("$name.port", value.port)
                    row("$name.remoteHost", value.remoteHost)
                    row("$name.uri", value.uri)
                }
            }

            for ((name, parameters) in listOf(
                "Query parameters" to request.queryParameters,
                "Headers" to request.headers
            )) {
                h2 {
                    +name
                }
                if (parameters.isEmpty()) {
                    +"empty"
                } else {
                    table {
                        for ((key, value) in parameters.flattenEntries()) {
                            row(key, value)
                        }
                    }
                }
            }

            h2 {
                +"Cookies"
            }
            table {
                for ((key, value) in request.cookies.rawCookies) {
                    row(key, value)
                }
            }

            if (includeServerInfo) {
                h2 {
                    +"Environment variables"
                }
                table {
                    for ((key, value) in System.getenv()) {
                        row(key, value)
                    }
                }
            }
        }
    }
}
