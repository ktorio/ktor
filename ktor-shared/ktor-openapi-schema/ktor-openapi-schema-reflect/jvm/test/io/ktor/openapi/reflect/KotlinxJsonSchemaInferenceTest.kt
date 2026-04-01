/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi.reflect

import io.ktor.openapi.*

class KotlinxJsonSchemaInferenceTest : AbstractSchemaInferenceTest(
    KotlinxSerializerJsonSchemaInference.Default,
    "kotlinx"
)
