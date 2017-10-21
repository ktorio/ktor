package io.ktor.server.testing

object On

object It

@Suppress("UNUSED_PARAMETER")
fun on(comment: String, body: On.() -> Unit) = On.body()

@Suppress("UNUSED_PARAMETER")
inline fun On.it(description: String, body: It.() -> Unit) = It.body()
