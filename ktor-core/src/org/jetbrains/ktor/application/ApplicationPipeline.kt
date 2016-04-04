package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*

val PipelineContext<ApplicationCall>.call: ApplicationCall get() = subject
fun PipelineContext<ApplicationCall>.respond(message: Any) = call.respond(message)
