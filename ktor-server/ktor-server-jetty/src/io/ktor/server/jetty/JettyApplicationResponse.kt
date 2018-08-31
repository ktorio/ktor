package io.ktor.server.jetty

import io.ktor.response.*
import io.ktor.server.jetty.internal.*
import io.ktor.server.servlet.*
import org.eclipse.jetty.server.*
import javax.servlet.http.*
import kotlin.coroutines.*

class JettyApplicationResponse(
    call: AsyncServletApplicationCall,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    engineContext: CoroutineContext,
    userContext: CoroutineContext,
    private val baseRequest: Request
) : AsyncServletApplicationResponse(
    call,
    servletRequest,
    servletResponse,
    engineContext,
    userContext,
    JettyUpgradeImpl
) {

    override fun push(builder: ResponsePushBuilder) {
        if (baseRequest.isPushSupported) {
            baseRequest.pushBuilder.apply {
                this.method(builder.method.value)
                this.path(builder.url.encodedPath)
                val query = builder.url.buildString().substringAfter('?', "").takeIf { it.isNotEmpty() }
                if (query != null) {
                    queryString(query)
                }

                push()
            }
        } else {
            super.push(builder)
        }
    }
}