package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*

class ResponseCookies(private val response: ApplicationResponse, private val request: ApplicationRequest) {
    private val originScheme = request.origin.scheme

    operator fun get(name: String): Cookie? = response.headers.values("Set-Cookie").map { parseServerSetCookieHeader(it) }.firstOrNull { it.name == name }
    fun append(item: Cookie) {
        if (item.secure && originScheme != "https") {
            throw IllegalArgumentException("You should set secure cookie only via secure transport (HTTPS)")
        }
        response.headers.append("Set-Cookie", renderSetCookieHeader(item))
    }

    fun append(name: String,
               value: String,
               encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
               maxAge: Int = 0,
               expires: Temporal? = null,
               domain: String = "",
               path: String = "",
               secure: Boolean = false,
               httpOnly: Boolean = false,
               extensions: Map<String, String?> = emptyMap()) {
        append(Cookie(
                name,
                value,
                encoding,
                maxAge,
                expires,
                domain,
                path,
                secure,
                httpOnly,
                extensions
                     ))
    }

    fun appendExpired(name: String, domain: String = "", path: String = "") {
        append(name, "", domain = domain, path = path, expires = Instant.EPOCH)
    }
}