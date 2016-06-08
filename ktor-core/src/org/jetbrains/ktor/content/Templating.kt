package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import kotlin.reflect.*

interface TemplateEngine<C : Any, out R> where R : FinalContent, R : Resource {
    val contentClass: KClass<C>
    fun process(content: C): R
}

inline fun <reified C : Any> Pipeline<ApplicationCall>.templating(engine: TemplateEngine<C, *>) {
    val javaType = engine.contentClass.java
    intercept(ApplicationCallPipeline.Call) { call ->
        call.interceptRespond(RespondPipeline.After) { obj ->
            if (javaType.isInstance(obj)) {
                call.respond(engine.process(obj as C))
            }
        }
    }
}
