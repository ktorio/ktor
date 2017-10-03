package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

val PipelineContext<*, ApplicationCall>.call: ApplicationCall get() = context
val PipelineContext<*, ApplicationCall>.application get() = call.application
