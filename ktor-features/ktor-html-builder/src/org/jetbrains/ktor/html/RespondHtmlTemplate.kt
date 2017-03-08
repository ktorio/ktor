package org.jetbrains.ktor.html

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

suspend fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplate(template: TTemplate, status: HttpStatusCode = HttpStatusCode.OK, body: TTemplate.() -> Unit) {
    template.body()
    respondHtml(status) { with(template) { apply() } }
}