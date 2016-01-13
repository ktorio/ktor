package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.time.*
import java.time.temporal.*

public class ResponseCookies(private val response: ApplicationResponse) {
    public operator fun get(name: String): Cookie? = response.headers.values("Set-Cookie").map { parseServerSetCookieHeader(it) }.firstOrNull { it.name == name }
    public fun append(item: Cookie): Unit = response.headers.append("Set-Cookie", renderSetCookieHeader(item))
    public fun intercept(handler: (cookie: Cookie, next: (value: Cookie) -> Unit) -> Unit) {
        response.headers.intercept { name, value, next ->
            if (name == "Set-Cookie") {
                handler(parseServerSetCookieHeader(value)) { intercepted ->
                    next(name, renderSetCookieHeader(intercepted))
                }
            } else {
                next(name, value)
            }
        }
    }

    public fun append(name: String,
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

    public fun appendExpired(name: String, domain: String = "", path: String = "") {
        append(name, "", domain = domain, path = path, expires = Instant.EPOCH)
    }
}