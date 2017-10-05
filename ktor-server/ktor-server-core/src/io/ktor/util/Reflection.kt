package io.ktor.util

import kotlin.reflect.*
import kotlin.reflect.jvm.*

fun KFunction<*>.qualifiedName(): String = javaMethod?.declaringClass?.name + name
