package org.jetbrains.ktor.html

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplate(template: TTemplate, status: HttpStatusCode = HttpStatusCode.OK, body: TTemplate.() -> Unit): Nothing {
    template.body()
    respondHtml(status) { with(template) { apply() } }
}