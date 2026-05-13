package io.ktor.openapi

data class OpenApiProcessorConfig(
    val enabled: Boolean,
    val codeInference: Boolean,
    val debug: Boolean,
    val onlyCommented: Boolean,
    val logDir: String?,
)