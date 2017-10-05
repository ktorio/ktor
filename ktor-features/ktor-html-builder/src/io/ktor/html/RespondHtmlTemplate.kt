package io.ktor.html

import kotlinx.html.*
import io.ktor.application.*
import io.ktor.http.*

suspend fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplate(template: TTemplate, status: HttpStatusCode = HttpStatusCode.OK, body: TTemplate.() -> Unit) {
    template.body()
    respondHtml(status) { with(template) { apply() } }
}