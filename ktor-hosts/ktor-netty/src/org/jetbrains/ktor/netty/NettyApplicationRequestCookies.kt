package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.cookie.*
import org.jetbrains.ktor.request.*
import java.util.*

internal class NettyApplicationRequestCookies(request: ApplicationRequest) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        val cookieHeaders = request.headers.getAll("Cookie") ?: return emptyMap()
        val map = HashMap<String, String>(cookieHeaders.size)
        for (cookieHeader in cookieHeaders) {
            val cookies = ServerCookieDecoder.LAX.decode(cookieHeader).associateBy({ it.name() }, { it.value() })
            map.putAll(cookies)
        }
        return map
    }
}