package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.pipeline.*
import kotlin.reflect.*

interface TemplateEngine<C : Any, out R> where R : FinalContent, R : Resource {
    val contentClass: KClass<C>
    fun process(content: C): R
}

inline fun <reified C : Any> Pipeline<ApplicationCall>.templating(engine: TemplateEngine<C, *>) {
    intercept(ApplicationCallPipeline.Call) { call ->
        call.transform.register<C>({ true }, { obj -> engine.process(obj) })
    }
}
