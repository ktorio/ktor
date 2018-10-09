package io.ktor.websocket

import io.ktor.http.cio.websocket.FrameType
import io.ktor.http.cio.websocket.Frame

@Deprecated(
    "Use io.ktor.http.cio.websocket.FrameType instead",
    replaceWith = ReplaceWith("FrameType", "io.ktor.http.cio.websocket.*"),
    level = DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation", "unused")
typealias FrameType = FrameType

@Deprecated(
    "Use io.ktor.http.cio.websocket.Frame instead",
    replaceWith = ReplaceWith("Frame", "io.ktor.http.cio.websocket.*"),
    level = DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation", "unused")
typealias Frame = Frame
