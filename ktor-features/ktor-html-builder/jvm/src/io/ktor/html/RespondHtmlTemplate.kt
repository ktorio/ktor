/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.html

import kotlinx.html.*
import io.ktor.application.*
import io.ktor.http.*

suspend fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplate(template: TTemplate, status: HttpStatusCode = HttpStatusCode.OK, body: TTemplate.() -> Unit) {
    template.body()
    respondHtml(status) { with(template) { apply() } }
}
