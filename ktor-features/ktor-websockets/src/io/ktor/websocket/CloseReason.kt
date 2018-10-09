package io.ktor.websocket

import io.ktor.http.cio.websocket.CloseReason

@Deprecated(
    "Use io.ktor.http.cio.websocket.CloseReason instead",
    replaceWith = ReplaceWith("CloseReason", "io.ktor.http.cio.websocket.*"),
    level = DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation", "unused")
typealias CloseReason = CloseReason
