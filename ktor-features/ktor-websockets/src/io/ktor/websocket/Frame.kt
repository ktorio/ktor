package io.ktor.websocket

import io.ktor.http.cio.websocket.FrameType
import io.ktor.http.cio.websocket.Frame

@Deprecated(
    "Use io.ktor.http.cio.websocket.FrameType instead",
    replaceWith = ReplaceWith("FrameType", "io.ktor.http.cio.websocket.*")
)
typealias FrameType = FrameType

@Deprecated(
    "Use io.ktor.http.cio.websocket.Frame instead",
    replaceWith = ReplaceWith("Frame", "io.ktor.http.cio.websocket.*")
)
typealias Frame = Frame
