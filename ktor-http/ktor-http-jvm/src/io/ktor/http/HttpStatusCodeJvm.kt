package io.ktor.http

import kotlin.reflect.full.*

internal actual fun allStatusCodes(): List<HttpStatusCode> =
    HttpStatusCode.Companion::class.memberProperties
        .filter { it.returnType.classifier == HttpStatusCode::class }
        .map { it.get(HttpStatusCode.Companion) as HttpStatusCode }