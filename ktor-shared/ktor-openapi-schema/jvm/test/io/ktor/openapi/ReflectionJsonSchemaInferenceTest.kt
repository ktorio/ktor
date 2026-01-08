/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

class ReflectionJsonSchemaInferenceTest : AbstractSchemaInferenceTest(
    ReflectionJsonSchemaInference(object : SchemaReflectionAdapter {
        /**
         * Preserve property declaration order to match kotlinx-serialization output.
         */
        override fun <T : Any> getProperties(kClass: KClass<T>): Collection<KProperty1<T, *>> {
            val constructorParams = kClass.primaryConstructor?.parameters?.map { it.name } ?: return emptyList()
            val props = kClass.memberProperties.associateBy { it.name }
            return constructorParams.mapNotNull { props[it] }
        }
    })
)
