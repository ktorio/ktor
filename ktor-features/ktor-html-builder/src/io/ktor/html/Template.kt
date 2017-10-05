package io.ktor.html

import java.util.*

/**
 * A template that expands inside [TOuter]
 */
interface Template<in TOuter> {
    fun TOuter.apply()
}

/**
 * A placeholder that is inserted inside [TOuter]
 */
open class Placeholder<TOuter> {
    private var content: TOuter.(Placeholder<TOuter>) -> Unit = { }
    var meta: String = ""

    operator fun invoke(meta: String = "", content: TOuter.(Placeholder<TOuter>) -> Unit) {
        this.content = content
        this.meta = meta
    }

    fun apply(destination: TOuter) {
        destination.content(this)
    }
}

/**
 * Placeholder that can appear multiple times
 */
open class PlaceholderList<TOuter, TInner>() {
    private var items = ArrayList<PlaceholderItem<TInner>>()
    operator fun invoke(meta: String = "", content: TInner.(Placeholder<TInner>) -> Unit = {}) {
        val placeholder = PlaceholderItem<TInner>(items.size, items)
        placeholder(meta, content)
        items.add(placeholder)
    }

    fun isEmpty(): Boolean = items.size == 0
    fun apply(destination: TOuter, render: TOuter.(PlaceholderItem<TInner>) -> Unit) {
        for (item in items) {
            destination.render(item)
        }
    }
}

/**
 * Item of a placeholder list when it is expanded
 */
class PlaceholderItem<TOuter>(val index: Int, val collection: List<PlaceholderItem<TOuter>>) : Placeholder<TOuter>() {
    val first: Boolean get() = index == 0
    val last: Boolean get() = index == collection.lastIndex
}


/**
 * Inserts every element of placeholder list
 */
fun <TOuter, TInner> TOuter.each(items: PlaceholderList<TOuter, TInner>, itemTemplate: TOuter.(PlaceholderItem<TInner>) -> Unit): Unit {
    items.apply(this, itemTemplate)
}

/**
 * Inserts placeholder
 */
fun <TOuter> TOuter.insert(placeholder: Placeholder<TOuter>): Unit = placeholder.apply(this)

/**
 * A placeholder that is also a template
 */
open class TemplatePlaceholder<TTemplate> {
    private var content: TTemplate.() -> Unit = { }
    operator fun invoke(content: TTemplate.() -> Unit) {
        this.content = content
    }

    fun apply(template: TTemplate) {
        template.content()
    }
}

fun <TTemplate : Template<TOuter>, TOuter> TOuter.insert(template: TTemplate, placeholder: TemplatePlaceholder<TTemplate>) {
    placeholder.apply(template)
    with (template) { apply() }
}

fun <TOuter, TTemplate : Template<TOuter>> TOuter.insert(template: TTemplate, build: TTemplate.() -> Unit) {
    template.build()
    with (template) { apply() }
}
