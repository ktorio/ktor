/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.ktor.openapi.OpenApiSpecification
import kotlinx.serialization.encodeToString

private val yamlFormat by lazy {
    Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous
        )
    )
}

internal actual fun serializeToYaml(openApiSpec: OpenApiSpecification): String =
    yamlFormat.encodeToString(openApiSpec)
