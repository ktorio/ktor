package org.jetbrains.ktor.tests

object On

object It

fun on(comment: String, body: On.() -> Unit) = On.body()
inline fun On.it(description: String, body: It.() -> Unit) = It.body()
