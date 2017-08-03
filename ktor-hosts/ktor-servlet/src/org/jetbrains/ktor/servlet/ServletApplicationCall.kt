package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.response.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationCall(application: Application,
                                  protected val servletRequest: HttpServletRequest,
                                  protected val servletResponse: HttpServletResponse,
                                  override val bufferPool: ByteBufferPool,
                                  pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit,
                                  hostContext: CoroutineContext,
                                  userAppContext: CoroutineContext) : BaseApplicationCall(application) {

    override val request: ServletApplicationRequest = ServletApplicationRequest(this, servletRequest)
    override val response: ServletApplicationResponse = ServletApplicationResponse(this, servletRequest, servletResponse, hostContext, userAppContext, pushImpl)
}