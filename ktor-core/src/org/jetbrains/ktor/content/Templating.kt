package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import kotlin.reflect.*

interface TemplateEngine<C : Any, R> where R : StreamContent, R : HasContentType {
    val contentClass: KClass<C>
    fun process(content: C): R
}

inline fun <reified C : Any> InterceptApplicationCall.templating(engine: TemplateEngine<C, *>) {
    val javaType = engine.contentClass.java
    intercept { call ->
        call.interceptRespond { obj ->
            if (javaType.isInstance(obj)) {
                respond(engine.process(obj as C))
            }
        }
    }
}
