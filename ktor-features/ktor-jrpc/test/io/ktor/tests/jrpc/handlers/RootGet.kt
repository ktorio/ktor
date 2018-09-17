package io.ktor.tests.jrpc.handlers

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.pipeline.PipelineContext
import io.ktor.response.respondText
import java.text.SimpleDateFormat
import java.util.*

val startTimeString: String = "Server is running\nStarted at ${Date(System.currentTimeMillis()).let {
    val df = SimpleDateFormat("HH:mm:ss dd/MM/yyyy")
    df.format(it)
}}"

suspend fun PipelineContext<Unit, ApplicationCall>.rootHandler() {
    call.respondText(startTimeString)
}