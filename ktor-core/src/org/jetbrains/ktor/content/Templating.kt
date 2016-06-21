package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.transform.*
import kotlin.reflect.*

interface TemplateEngine<C : Any, out R> where R : FinalContent, R : Resource {
    val contentClass: KClass<C>
    fun process(content: C): R
}

inline fun <reified C : Any> Application.templating(engine: TemplateEngine<C, *>) {
    feature(TransformationSupport).register<C> { model -> engine.process(model) }
}
