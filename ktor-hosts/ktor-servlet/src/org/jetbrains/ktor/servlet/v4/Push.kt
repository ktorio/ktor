package org.jetbrains.ktor.servlet.v4

import org.jetbrains.ktor.response.*
import javax.servlet.http.*

@Suppress("unused")
fun doPush(request: HttpServletRequest, builder: ResponsePushBuilder): Boolean {
    request.newPushBuilder()?.apply {
        this.method(builder.method.value)
        this.path(builder.url.encodedPath)

        val query = builder.url.build().substringAfter('?', "").takeIf { it.isNotEmpty() }
        if (query != null) {
            queryString(query)
        }

        push()
        return true
    }

    return false
}
