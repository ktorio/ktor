package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.response.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationCall(application: Application,
                                  val servletRequest: HttpServletRequest,
                                  val servletResponse: HttpServletResponse,
                                  override val bufferPool: ByteBufferPool,
                                  pushImpl: (ResponsePushBuilder) -> Boolean,
                                  hostContext: CoroutineContext,
                                  userAppContext: CoroutineContext) : BaseApplicationCall(application) {

    override val request: ServletApplicationRequest = ServletApplicationRequest(this, servletRequest)
    override val response: ServletApplicationResponse = ServletApplicationResponse(this, servletRequest, servletResponse, hostContext, userAppContext, pushImpl)
}