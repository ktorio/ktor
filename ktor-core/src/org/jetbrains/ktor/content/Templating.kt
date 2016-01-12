package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import kotlin.reflect.*

interface TemplateEngine<C : Any, R> where R : HasContent, R : HasContentType {
    val contentClass: KClass<C>
    fun process(content: C): R
}

inline fun <reified C : Any> Application.templating(engine: TemplateEngine<C, *>) {
    val javaType = engine.contentClass.java
    intercept { next ->
        response.interceptSend { obj, next ->
            if (javaType.isInstance(obj)) {
                response.send(engine.process(obj as C))
            } else {
                next(obj)
            }
        }
        next()
    }
}

inline fun <reified C : Any> RoutingEntry.templating(engine: TemplateEngine<C, *>) {
    val javaType = engine.contentClass.java
    intercept { next ->
        response.interceptSend { obj, next ->
            if (javaType.isInstance(obj)) {
                response.send(engine.process(obj as C))
            } else {
                next(obj)
            }
        }
        next()
    }
}
