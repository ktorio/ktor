/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.html

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import kotlinx.html.*

public suspend fun <TTemplate : Template<HTML>> RoutingCall.respondHtmlTemplate(
    template: TTemplate,
    status: HttpStatusCode = HttpStatusCode.OK,
    body: TTemplate.() -> Unit
): Unit = call.respondHtmlTemplate(template, status, body)

public suspend fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplate(
    template: TTemplate,
    status: HttpStatusCode = HttpStatusCode.OK,
    body: TTemplate.() -> Unit
) {
    template.body()
    respondHtml(status) { with(template) { apply() } }
}
