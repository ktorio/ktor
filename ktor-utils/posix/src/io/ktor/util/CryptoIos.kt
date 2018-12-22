package io.ktor.util

@InternalAPI
actual fun generateNonce(): String = error("[generateNonce] is not supported on iOS")

@InternalAPI
actual fun Digest(name: String): Digest = error("[Digest] is not supported on iOS")
